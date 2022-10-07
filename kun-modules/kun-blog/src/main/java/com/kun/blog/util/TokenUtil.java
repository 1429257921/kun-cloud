package com.kun.blog.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.kun.blog.config.SecurityProperties;
import com.kun.blog.entity.vo.JwtUser;
import com.kun.common.redis.service.RedisService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 工具类
 *
 * @author gzc
 * @since 2022/10/7 22:46
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUtil {

    private final SecurityProperties securityProperties;
    private final RedisService redisService;

    /**
     * 权限缓存前缀
     */
    private static final String REDIS_PREFIX_AUTH = "auth:";
    /**
     * 用户信息缓存前缀
     */
    private static final String REDIS_PREFIX_USER = "user-details:";

    /**
     * 获取用户名
     *
     * @param token Token
     * @return String
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 获取过期时间
     *
     * @param token Token
     * @return Date
     */
    public Date getExpiredFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * 获得 Claims
     *
     * @param token Token
     * @return Claims
     */
    private Claims getClaimsFromToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .setSigningKey(securityProperties.getSecret())
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.warn("getClaimsFromToken exception", e);
            claims = null;
        }
        return claims;
    }

    /**
     * 计算过期时间
     *
     * @return Date
     */
    private Date generateExpired() {
        return new Date(System.currentTimeMillis() + securityProperties.getTokenValidityInSeconds() * 1000);
    }

    /**
     * 判断 Token 是否过期
     *
     * @param token Token
     * @return Boolean
     */

    private Boolean isTokenExpired(String token) {
        Date expirationDate = getExpiredFromToken(token);
        return expirationDate.before(new Date());
    }

    /**
     * 生成 Token
     *
     * @param userDetails 用户信息
     * @return String
     */

    public String generateToken(UserDetails userDetails) {
        String secret = securityProperties.getSecret();
        String token = Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setExpiration(generateExpired())
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();

        String key = REDIS_PREFIX_AUTH + userDetails.getUsername() + ":" + token;
//        redisUtils.set(key, token, securityProperties.getTokenValidityInSeconds() / 1000);
        redisService.setCacheObject(key, token, securityProperties.getTokenValidityInSeconds(), TimeUnit.MILLISECONDS);
        putUserDetails(userDetails);
        return token;
    }

    /**
     * 验证 Token
     *
     * @param token Token
     * @return Boolean
     */

    public Boolean validateToken(String token) {
        final String username = getUsernameFromToken(token);
        String key = REDIS_PREFIX_AUTH + username + ":" + token;
//        Object data = redisUtils.get(key);
        Object data = redisService.getCacheObject(key);
        String redisToken = data == null ? null : data.toString();
        return StrUtil.isNotEmpty(token) && !isTokenExpired(token) && token.equals(redisToken);
    }

    /**
     * 移除 Token
     *
     * @param token Token
     */

    public void removeToken(String token) {
        final String username = getUsernameFromToken(token);
        String key = REDIS_PREFIX_AUTH + username + ":" + token;
//        redisUtils.del(key);
        redisService.deleteObject(key);
        delUserDetails(username);
    }

    /**
     * 获得用户信息 Json 字符串
     *
     * @param token Token
     * @return String
     */

    protected String getUserDetailsString(String token) {
        final String username = getUsernameFromToken(token);
        String key = REDIS_PREFIX_USER + username;
//        Object data = redisUtils.get(key);
        Object data = redisService.getCacheObject(key);
        return data == null ? null : data.toString();
    }

    /**
     * 获得用户信息
     *
     * @param token Token
     * @return UserDetails
     */

    public UserDetails getUserDetails(String token) {
        String userDetailsString = getUserDetailsString(token);
        if (userDetailsString != null) {
//            return new Gson().fromJson(userDetailsString, JwtUser.class);
            return JSON.parseObject(userDetailsString, JwtUser.class);
        }
        return null;
    }

    /**
     * 存储用户信息
     *
     * @param userDetails 用户信息
     */

    private void putUserDetails(UserDetails userDetails) {
        String key = REDIS_PREFIX_USER + userDetails.getUsername();
//        redisUtils.set(key, new Gson().toJson(userDetails), securityProperties.getTokenValidityInSeconds() / 1000);
        redisService.setCacheObject(key, JSON.toJSONString(userDetails), securityProperties.getTokenValidityInSeconds(), TimeUnit.MILLISECONDS);
    }


    /**
     * 删除用户信息
     *
     * @param username 用户名
     */

    private void delUserDetails(String username) {
        String key = REDIS_PREFIX_USER + username;
//        redisUtils.del(key);
        redisService.deleteObject(key);
    }

    public String getToken(HttpServletRequest request) {
        final String requestHeader = request.getHeader(securityProperties.getHeader());
        if (requestHeader != null && requestHeader.startsWith(securityProperties.getTokenStartWith())) {
            return requestHeader.substring(7);
        }
        return null;
    }

    public static void main(String[] args) {
        String key = Base64.getEncoder().encodeToString("123".getBytes());
        Claims claims = Jwts.parser().setSigningKey(key)
                .parseClaimsJws("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJhZG1pbiIsInNjb3BlIjpbImFwcCIsIndyaXRlIl0sInVpbiI6MSwiZXhwIjoxNTc1MDE1ODgzLCJhdXRob3JpdGllcyI6WyJST0xFX1VTRVIiXSwianRpIjoiYjdiYjQ1NTQtNTQ4OS00YTg5LWI3NjQtNzNjODI0YzljNGMyIiwiY2xpZW50X2lkIjoibHZoYWliYW8ifQ.x7QZxRAR1wuX_YNLi6EzRJ1iaKr1rIEUgjtYF0oSx5k").getBody();
        System.out.println(JSON.toJSONString(claims));
    }
}
