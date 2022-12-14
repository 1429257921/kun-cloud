package com.kun.blog.service;

import com.kun.blog.entity.po.KunUser;
import com.kun.blog.entity.vo.GetUserDetailsVO;
import com.kun.common.database.service.BaseService;

/**
 * 用户表业务接口
 *
 * @author gzc
 * @since 2022-10-07 21:05:29
 */
public interface IKunUserService extends BaseService<KunUser> {

    /**
     * 获取个人信息
     *
     * @return
     */
    GetUserDetailsVO getUserDetails();
}
