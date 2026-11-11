package com.personnel.dto.req;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EmployeeQueryReqDTO {

    @NotNull
    @Min(1)
    @Max(1000000)
    private Integer pageNum = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    @Size(max = 50)
    private String keyword;

    @Positive
    private Long departmentId;

    @Pattern(regexp = "ACTIVE|RESIGNED", message = "在职状态不合法")
    private String employmentStatus;
}
