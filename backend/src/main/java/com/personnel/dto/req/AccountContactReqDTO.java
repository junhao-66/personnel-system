package com.personnel.dto.req;

import jakarta.validation.constraints.Size;

public record AccountContactReqDTO(@Size(max = 20) String phone) {}
