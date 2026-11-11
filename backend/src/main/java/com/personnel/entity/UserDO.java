package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_user")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userName;
    private String passwordHash;
    private String realName;
    private String phone;
    private String roleCode;
    private String auditStatus;
    private String auditReason;
    private LocalDateTime reviewedAt;
    private Long authVersion;
    private String userStatus;
    private Long departmentId;
    private Long positionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
