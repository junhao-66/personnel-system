package com.personnel.entity;

import lombok.Data;

/** Aggregate query projection; one row per department and latest personality type. */
@Data
public class TalentGroupDO {

    private Long departmentId;
    private String departmentName;
    private String typeCode;
    private Long employeeCount;
    private Long assessmentCount;
}
