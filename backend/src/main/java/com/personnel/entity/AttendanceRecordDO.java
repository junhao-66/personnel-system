package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("attendance_record")
public class AttendanceRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;
    private Long userId;
    private LocalDate attendanceDate;
    private LocalDateTime clockInTime;
    private LocalDateTime clockOutTime;
    private LocalTime workStartTime;
    private LocalTime workEndTime;
    private Integer isRestDay;
    private Integer isLate;
    private Integer isEarly;
}
