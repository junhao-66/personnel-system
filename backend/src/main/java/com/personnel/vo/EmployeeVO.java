package com.personnel.vo;

import java.time.LocalDate;

public record EmployeeVO(
    Long id,
    String userName,
    String realName,
    String phone,
    String roleCode,
    Long departmentId,
    String departmentName,
    Long positionId,
    String positionName,
    LocalDate hireDate,
    String employmentStatus,
    String userStatus
) {}
