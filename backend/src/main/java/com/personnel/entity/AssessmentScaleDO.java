package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("assessment_scale")
public class AssessmentScaleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;
    private String name;
    private String version;
    private String status;
    private String definitionJson;
    private String contentHash;
    private Long createBy;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
