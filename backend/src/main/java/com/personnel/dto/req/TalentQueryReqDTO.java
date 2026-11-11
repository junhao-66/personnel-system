package com.personnel.dto.req;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TalentQueryReqDTO {

    @NotNull
    @Min(1)
    @Max(1000000)
    private Integer pageNum = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    // 0 represents unassigned, rather than silently excluding people without an organization.
    @Min(0)
    private Long departmentId;

    @Min(0)
    private Long positionId;

    @NotNull
    @Pattern(regexp = "ACTIVE|RESIGNED|ALL")
    private String employmentStatus = "ACTIVE";

    @NotNull
    @Pattern(regexp = "ALL|COMPLETED|NOT_COMPLETED")
    private String assessmentStatus = "ALL";

    @Pattern(regexp = "[EI][SN][TF][JP]", message = "人格类型不合法")
    private String typeCode;
}
