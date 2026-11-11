package com.personnel.vo;

public record DepartmentVO(
    Long id,
    String departmentName,
    String departmentCode,
    Long managerUserId,
    Integer sortOrder,
    Integer isEnabled,
    Long selfApproverUserId
) {
    public DepartmentVO(
        Long id,
        String departmentName,
        String departmentCode,
        Long managerUserId,
        Integer sortOrder,
        Integer isEnabled
    ) {
        this(id, departmentName, departmentCode, managerUserId, sortOrder, isEnabled, null);
    }
}
