package com.personnel.vo;

public record AttendanceEmployeeVO(
    AttendanceRecordVO attendance,
    String userName,
    String realName,
    String departmentName
) {}
