package com.kun.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kun.blog.entity.po.UserLoginRecord;
import com.kun.blog.entity.req.UserLoginRecordPageListReq;
import com.kun.blog.entity.vo.UserLoginRecordPageListVO;
import com.kun.blog.mapper.UserLoginRecordMapper;
import com.kun.blog.service.IUserLoginRecordService;
import com.kun.common.database.entity.dto.PageParameter;
import com.kun.common.database.service.impl.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author gzc
 * @since 2023-01-05 18:04:37
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true, rollbackFor = Exception.class)
public class UserLoginRecordServiceImpl extends BaseServiceImpl<UserLoginRecordMapper, UserLoginRecord> implements IUserLoginRecordService {


    @Override
    @SuppressWarnings("ALL")
    public Page<UserLoginRecordPageListVO> pageList(PageParameter<UserLoginRecordPageListReq> pageParameter) {
        return super.page((Page) pageParameter, pageParameter.buildQueryWrapper(UserLoginRecord.class));
    }

}
