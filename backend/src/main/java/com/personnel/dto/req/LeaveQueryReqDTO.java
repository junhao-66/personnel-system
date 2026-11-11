package com.personnel.dto.req;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LeaveQueryReqDTO {

    @NotNull
    @Min(1)
    @Max(1000000)
    private Integer pageNum = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    @Pattern(regexp = "PENDING|APPROVED|REJECTED")
    private String status;
}
