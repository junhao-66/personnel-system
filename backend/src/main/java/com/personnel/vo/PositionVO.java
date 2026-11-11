package com.personnel.vo;

public record PositionVO(
    Long id,
    String positionName,
    String positionCode,
    Long departmentId,
    String description,
    Integer isEnabled
) {}
