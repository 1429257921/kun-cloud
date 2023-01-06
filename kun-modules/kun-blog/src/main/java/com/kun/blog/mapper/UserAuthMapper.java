package com.kun.blog.mapper;

import com.kun.blog.entity.po.UserAuth;
import com.kun.common.database.mapper.CoreMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坤坤云用户登录授权表k_user_auth表持久层接口
 *
 * @author gzc
 * @since 2023-01-05 18:04:37
 */
public interface UserAuthMapper extends CoreMapper<UserAuth> {

}
