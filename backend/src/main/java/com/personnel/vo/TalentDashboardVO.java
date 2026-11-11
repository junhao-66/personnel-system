package com.personnel.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TalentDashboardVO(
    long employeeCount,
    long completedCount,
    long notCompletedCount,
    long assessmentCount,
    BigDecimal completionRate,
    List<TypeDistribution> typeDistribution,
    List<DepartmentDistribution> departmentDistribution,
    LocalDateTime generatedAt,
    String scopeNotice
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TypeDistribution(String typeCode, long employeeCount, BigDecimal percent) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DepartmentDistribution(
        Long departmentId,
        String departmentName,
        long employeeCount,
        long completedCount,
        long notCompletedCount,
        long assessmentCount,
        BigDecimal completionRate
    ) {}
}
