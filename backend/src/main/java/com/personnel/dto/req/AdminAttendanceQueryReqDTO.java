package com.personnel.dto.req;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminAttendanceQueryReqDTO extends AttendanceQueryReqDTO {

    @Size(max = 50)
    private String keyword;

    @Positive
    private Long departmentId;
}
