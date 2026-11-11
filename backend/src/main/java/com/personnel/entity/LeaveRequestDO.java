package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("leave_request")
public class LeaveRequestDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;
    private Long userId;
    private Long departmentId;
    private String leaveType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
    private String status;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
