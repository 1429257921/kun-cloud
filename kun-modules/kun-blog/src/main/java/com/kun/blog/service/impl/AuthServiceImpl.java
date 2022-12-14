package com.kun.blog.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.kun.blog.entity.po.User;
import com.kun.blog.entity.po.UserAuth;
import com.kun.blog.entity.po.UserLoginRecord;
import com.kun.blog.entity.req.UserLoginReq;
import com.kun.blog.entity.req.UserRegisterReq;
import com.kun.blog.entity.req.ValidatedCodeReq;
import com.kun.blog.entity.req.ValidatedPhoneCodeReq;
import com.kun.blog.entity.vo.GetVerificationCodeVO;
import com.kun.blog.entity.vo.SendPhoneCodeVO;
import com.kun.blog.entity.vo.UserLoginVO;
import com.kun.blog.entity.vo.UserRegisterVO;
import com.kun.blog.enums.DeleteFlagEnum;
import com.kun.blog.enums.UserAuthLoginTypeEnum;
import com.kun.blog.enums.UserStatusEnum;
import com.kun.blog.security.JwtProperties;
import com.kun.blog.security.dto.JwtUser;
import com.kun.blog.service.*;
import com.kun.common.core.exception.Assert;
import com.kun.common.core.exception.BizException;
import com.kun.common.core.utils.ip.IPUtil;
import com.kun.common.core.utils.spring.ContextUtil;
import com.kun.common.redis.constants.CacheConstants;
import com.kun.common.redis.service.RedisService;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * @author gzc
 * @since 2022/10/7 18:19
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    /**
     * ????????????(??????)
     */
    public static final int EXPIRES_TIME = 5;

    private final RedisService redisService;
    private final UserDetailsService userDetailsService;
    private final IUserService userService;
    private final IUserAuthService userAuthService;
    private final IUserLoginRecordService userLoginRecordService;
    private final JwtProperties jwtProperties;
    private final JwtTokenService jwtTokenService;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final PasswordEncoder passwordEncoder;

    @Value("${user-init.head-portrait}")
    private String headPortrait;
    @Value("${sms-config.test}")
    private Boolean smsTest;

    @Override
    public GetVerificationCodeVO getCode() {
        // ???????????? https://gitee.com/whvse/EasyCaptcha
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(111, 36);
        // ?????????????????????????????????
        captcha.setLen(2);
        // ?????????????????????
        String result;
        try {
            result = new Double(Double.parseDouble(captcha.text())).intValue() + "";
        } catch (Exception e) {
            result = captcha.text();
        }
        log.info("?????????->{}", result);
        String uuid = jwtProperties.getCodeKey() + IdUtil.simpleUUID();
        // ??????
        redisService.setCacheObject(uuid, result, 1L, TimeUnit.MINUTES);
        // ???????????????
        GetVerificationCodeVO getVerificationCodeVO = new GetVerificationCodeVO();
        getVerificationCodeVO.setUuid(uuid);
        getVerificationCodeVO.setImg(captcha.toBase64());
        return getVerificationCodeVO;
    }

    @Override
    public void validatedCode(ValidatedCodeReq codeReq) {
        String code = redisService.getCacheObject(codeReq.getUuid());
        if (StrUtil.isBlank(code) || StrUtil.isBlank(codeReq.getCode()) || !code.equals(codeReq.getCode())) {
            throw new BizException("???????????????");
        }
        // ???????????????
        redisService.deleteObject(codeReq.getUuid());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserRegisterVO register(UserRegisterReq userRegisterReq) {
        // ???????????????
        checkTimeStamp(userRegisterReq.getTimestamp());
        // ???????????????
        checkCode(userRegisterReq.getPhone(), userRegisterReq.getCode());

        // ??????????????????
        User user = User.builder().phone(userRegisterReq.getPhone()).nickName("??????" + userRegisterReq.getPhone()).headPortrait(headPortrait).status(UserStatusEnum.ACTIVE.getCode()).registerSource(UserAuthLoginTypeEnum.PHONE_CODE.getCode()).deleted((byte) DeleteFlagEnum.ACTIVE.getCode()).createBy("").updateBy("").build();
        try {
            if (!userService.save(user)) {
                throw new BizException("????????????????????????");
            }
        } catch (DuplicateKeyException dke) {
            throw new BizException("???????????????");
        }

        // ??????????????????????????????
        UserAuth userAuth = userAuthService.getByUserId(user.getId(), UserAuthLoginTypeEnum.PHONE_CODE);
        if (userAuth == null) {
            userAuth = UserAuth.builder().loginType(UserAuthLoginTypeEnum.PHONE_CODE.getCode()).account(userRegisterReq.getPhone()).userId(user.getId()).build();
            Assert.isTrue(userAuthService.save(userAuth), "????????????????????????????????????");
        }
        // ???????????????????????????
        redisService.deleteObject(CacheConstants.SMS_AUTH_CODE + userRegisterReq.getPhone());

        return new UserRegisterVO();
    }

    @Override
    public UserLoginVO login(UserLoginReq userLoginReq) {
        // ???????????????
        checkTimeStamp(userLoginReq.getTimestamp());
        // ??????????????????
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userLoginReq.getPhone(), userLoginReq.getPassword());
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // ??????????????????
        JwtUser jwtUser = (JwtUser) Assert.notNull(userDetailsService.loadUserByUsername(userLoginReq.getPhone()), "???????????????");

        // ??????????????????
        UserAuth userAuth = Assert.notNull(getUserAuth(userLoginReq, jwtUser), "????????????????????????");

        // ????????????
        String token = jwtTokenService.generateToken(jwtUser);
        log.info("?????????->{}, ????????????->{}", userLoginReq.getPhone(), token);
        // ???????????????????????????
        redisService.deleteObject(CacheConstants.SMS_AUTH_CODE + userLoginReq.getPhone());
        // ?????? token ??? ????????????
        UserLoginVO vo = UserLoginVO.builder().token(jwtProperties.getTokenStartWith() + token)
                .user(jwtUser)
                .expireTime(jwtProperties.getTokenValidityInSeconds())
                .build();

        UserLoginRecord userLoginRecord = UserLoginRecord.builder()
                .authId(userAuth.getId())
                .loginStatus(userAuth.getLoginType())
                .userId(userAuth.getUserId())
                .ip(IPUtil.getIp(ContextUtil.getRequest()))
                .os("").osVersion("").appVersion("").mac("")
                .build();
        Assert.isTrue(userLoginRecordService.save(userLoginRecord), "??????????????????????????????");
        return vo;
    }

    private UserAuth getUserAuth(UserLoginReq userLoginReq, JwtUser jwtUser) {
        UserAuth userAuth;
        // ????????????????????????
        if (UserAuthLoginTypeEnum.PHONE_CODE.getCode() == userLoginReq.getType()) {
            // ???????????????
            checkCode(userLoginReq.getPhone(), userLoginReq.getCode());
            // ????????????????????????
            userAuth = userAuthService.getByUserId(jwtUser.getId(), UserAuthLoginTypeEnum.PHONE_CODE);
            if (userAuth == null) {
                userAuth = UserAuth.builder().userId(jwtUser.getId()).account(jwtUser.getPhone()).loginType(UserAuthLoginTypeEnum.PHONE_CODE.getCode()).build();
                Assert.isTrue(userAuthService.save(userAuth), "??????????????????????????????");
            }
        }
        // ?????????????????????
        else if (UserAuthLoginTypeEnum.PHONE_KEY.getCode() == userLoginReq.getType()) {
            userAuth = userAuthService.getByUserId(jwtUser.getId(), UserAuthLoginTypeEnum.PHONE_KEY);
            if (userAuth == null) {
                userAuth = UserAuth.builder().userId(jwtUser.getId()).account(jwtUser.getPhone()).loginType(UserAuthLoginTypeEnum.PHONE_KEY.getCode()).build();
                Assert.isTrue(userAuthService.save(userAuth), "??????????????????????????????");
            }
        }
        // ??????????????????
        else if (UserAuthLoginTypeEnum.ACCOUNT.getCode() == userLoginReq.getType()) {
            Assert.notBlank(userLoginReq.getPassword(), "????????????");
            // rsa??????
//            String password = RsaUtil.decryptByPrivateKey(userLoginReq.getPassword());
            // ????????????????????????
            userAuth = Assert.notNull(userAuthService.getByUserId(jwtUser.getId(), UserAuthLoginTypeEnum.ACCOUNT), "??????????????????");
            // ????????????
//            if (!passwordEncoder.matches(password, userAuth.getPassword())) {
//                throw new BizException("???????????????");
//            }
            if (!userAuth.getPassword().equals(userLoginReq.getPassword())) {
                throw new BizException("???????????????");
            }
            if (!userLoginReq.getPhone().equals(userAuth.getAccount())) {
                throw new BizException("????????????!");
            }
        } else {
            throw new BizException("?????????????????????");
        }
        return userAuth;
    }

    @Override
    public SendPhoneCodeVO sendPhoneCode(String phone) {
        Integer code = null;
        if (smsTest) {
            code = 1234;
            redisService.setCacheObject(CacheConstants.SMS_AUTH_CODE + phone, code, 5L, TimeUnit.MINUTES);
        }

        return SendPhoneCodeVO.builder().code(code).build();
    }

    @Override
    public void validatedPhoneCode(ValidatedPhoneCodeReq validatedCodeReq) {
        String redisKey = CacheConstants.SMS_AUTH_CODE + validatedCodeReq.getPhone();
        Integer code = redisService.getCacheObject(redisKey);
        if (code == null) {
            throw new BizException("???????????????");
        }
        if (!validatedCodeReq.getCode().equals(code)) {
            throw new BizException("???????????????");
        }
    }

    /**
     * ???????????????
     *
     * @param timeStamp ?????????(??????)
     */
    private void checkTimeStamp(Long timeStamp) {
        long between = DateUtil.between(DateUtil.date(timeStamp), DateUtil.date(), DateUnit.MINUTE);
        if (between > EXPIRES_TIME) {
            throw new BizException("??????????????????");
        }
    }

    /**
     * ???????????????????????????
     *
     * @param phone ?????????
     * @param code  ???????????????
     */
    private void checkCode(String phone, Integer code) {
        ValidatedPhoneCodeReq validatedPhoneCodeReq = ValidatedPhoneCodeReq.builder().phone(phone).code(code).build();
        this.validatedPhoneCode(validatedPhoneCodeReq);
    }

}
