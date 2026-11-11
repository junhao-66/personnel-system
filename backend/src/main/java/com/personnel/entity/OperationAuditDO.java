package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("operation_audit")
public class OperationAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;
    private Long actorUserId;
    private String actorRole;
    private String operation;
    private String targetType;
    private Long targetId;
    private String changedFields;
    private LocalDateTime occurredAt;
}
