package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("assessment_session")
public class AssessmentSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;
    private Long userId;
    private Long scaleId;
    private String scaleSnapshot;
    private String contentHash;
    private String status;
    private Integer revision;
    private String answersJson;
    private String resultJson;
    private LocalDateTime consentAt;
    private LocalDateTime submittedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
