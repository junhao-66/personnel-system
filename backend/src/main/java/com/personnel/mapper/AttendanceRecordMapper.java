package com.personnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.entity.AttendanceRecordDO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecordDO> {
    Page<AttendanceRecordDO> selectDailyRecords(
        Page<AttendanceRecordDO> page,
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("keyword") String keyword,
        @Param("departmentId") Long departmentId
    );
    AttendanceRecordDO selectToday(@Param("userId") Long userId, @Param("date") LocalDate date);
    int clockOut(
        @Param("userId") Long userId,
        @Param("date") LocalDate date,
        @Param("time") LocalDateTime time,
        @Param("early") int early
    );
    Page<AttendanceRecordDO> selectRecords(
        Page<AttendanceRecordDO> page,
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("keyword") String keyword,
        @Param("departmentId") Long departmentId
    );
}
