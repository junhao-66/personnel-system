package com.personnel.dto.req;

import jakarta.validation.constraints.Positive;

public record DepartmentSelfApproverReqDTO(@Positive Long managerUserId) {}
