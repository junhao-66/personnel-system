package com.personnel.vo;

import java.util.List;

public record LeaveOptionsVO(
    List<String> leaveTypes,
    int minimumMinutes,
    boolean crossDayAllowed,
    String timeZone,
    String approverName,
    String unavailableReason
) {}
