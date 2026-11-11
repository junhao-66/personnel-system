package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("attendance_rule")
public class AttendanceRuleDO {

    @TableId
    private Long id;

    private Long enterpriseId;
    private LocalTime workStartTime;
    private LocalTime workEndTime;
}
