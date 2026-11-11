package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_position")
public class PositionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String positionName;
    private String positionCode;
    private Long departmentId;
    private String description;
    private Integer isEnabled;
    private LocalDateTime createTime;
}
