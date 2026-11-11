package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_preference_profile")
public class JobPreferenceProfileDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long positionId;
    private String version;
    private String status;
    private String definitionJson;
    private String contentHash;
    private Long createdBy;
    private LocalDateTime createdAt;
}
