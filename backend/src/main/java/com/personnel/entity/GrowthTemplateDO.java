package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("growth_template")
public class GrowthTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;
    private String title;
    private String status;
    private String contentHash;
    private String suggestedCycle;
    private String observationTarget;
    private String reviewPrompt;
    private String fallbackContext;
    private String fallbackAction;
    private Long createBy;
    private LocalDateTime createTime;
}
