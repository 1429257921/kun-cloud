package com.kun.blog.entity.req;

import com.kun.common.database.anno.Query;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 用户注册接口请求参数
 *
 * @author gzc
 * @since 2022/10/7 21:08
 **/
@Data
public class UserRegisterReq implements Serializable {
    private static final long serialVersionUID = -1242493306307174690L;
    /**
     * 用户名
     */
    @Query(propName = "username")
    @NotBlank(message = "用户名为空")
    private String userName;
    /**
     * 密码
     */
    @NotBlank(message = "密码为空")
    private String passWord;
    /**
     * 电子邮箱
     */
    private String email;
    /**
     * 验证码
     */
    private String code;
    /**
     * 时间戳
     */
    private String timestamp;
    /**
     * 注册类型
     */
    private Integer registerType;
}
