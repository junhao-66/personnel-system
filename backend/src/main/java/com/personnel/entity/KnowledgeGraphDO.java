package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_graph_version")
public class KnowledgeGraphDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;
    private String title;
    private String status;
    private String definitionJson;
    private String contentHash;
    private Long createBy;
    private LocalDateTime createTime;
}
