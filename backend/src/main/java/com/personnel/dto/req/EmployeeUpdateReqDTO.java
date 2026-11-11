package com.personnel.dto.req;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record EmployeeUpdateReqDTO(
    @NotBlank(message = "姓名不能为空") @Size(max = 50) String realName,
    @Size(max = 20) String phone,
    @Positive Long departmentId,
    @Positive Long positionId,
    @PastOrPresent(message = "入职日期不能晚于今天") LocalDate hireDate,
    @NotNull
    @Pattern(regexp = "ACTIVE|RESIGNED", message = "在职状态不合法")
    String employmentStatus
) {}
