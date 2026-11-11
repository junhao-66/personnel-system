package com.personnel.convert;

import com.personnel.entity.AttendanceRecordDO;
import com.personnel.entity.AttendanceRuleDO;
import com.personnel.vo.AttendanceRecordVO;
import com.personnel.vo.AttendanceRuleVO;

public final class AttendanceConvert {

    private AttendanceConvert() {}

    public static AttendanceRecordDO emptyDay(
        Long userId,
        java.time.LocalDate date,
        AttendanceRuleDO rule
    ) {
        AttendanceRecordDO row = new AttendanceRecordDO();
        row.setUserId(userId);
        row.setAttendanceDate(date);
        row.setWorkStartTime(rule.getWorkStartTime());
        row.setWorkEndTime(rule.getWorkEndTime());
        row.setIsRestDay(date.getDayOfWeek().getValue() >= 6 ? 1 : 0);
        return row;
    }

    public static AttendanceRecordVO toVO(
        AttendanceRecordDO row,
        com.personnel.common.util.AttendanceEvaluation.Result result
    ) {
        return new AttendanceRecordVO(
            row.getId(),
            row.getUserId(),
            row.getAttendanceDate(),
            row.getClockInTime(),
            row.getClockOutTime(),
            row.getWorkStartTime(),
            row.getWorkEndTime(),
            Integer.valueOf(1).equals(row.getIsRestDay()),
            result.late(),
            result.early(),
            result.status(),
            result.leaveMinutes(),
            result.missingIn(),
            result.missingOut(),
            result.requiredStart(),
            result.requiredEnd(),
            row.getId() == null ? "CURRENT_RULE" : "RECORDED_RULE"
        );
    }

    public static AttendanceRuleVO toRuleVO(AttendanceRuleDO rule) {
        return new AttendanceRuleVO(
            rule.getWorkStartTime(),
            rule.getWorkEndTime(),
            "Asia/Shanghai",
            true
        );
    }

    public static AttendanceRecordVO toVO(AttendanceRecordDO record) {
        if (record == null) return null;
        boolean restDay = Integer.valueOf(1).equals(record.getIsRestDay());
        boolean late = Integer.valueOf(1).equals(record.getIsLate());
        boolean early = Integer.valueOf(1).equals(record.getIsEarly());
        String status;
        if (restDay) status = "REST_DAY";
        else if (record.getClockOutTime() == null) status = late
            ? "LATE_IN_PROGRESS"
            : "IN_PROGRESS";
        else if (late && early) status = "LATE_AND_EARLY";
        else if (late) status = "LATE";
        else if (early) status = "EARLY";
        else status = "NORMAL";
        return new AttendanceRecordVO(
            record.getId(),
            record.getUserId(),
            record.getAttendanceDate(),
            record.getClockInTime(),
            record.getClockOutTime(),
            record.getWorkStartTime(),
            record.getWorkEndTime(),
            restDay,
            late,
            early,
            status
        );
    }
}
