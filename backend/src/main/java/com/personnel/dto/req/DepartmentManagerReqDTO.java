package com.personnel.dto.req;

import jakarta.validation.constraints.*;

public record DepartmentManagerReqDTO(@NotNull @Positive Long managerUserId) {}
