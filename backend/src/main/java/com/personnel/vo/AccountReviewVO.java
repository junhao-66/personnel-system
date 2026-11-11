package com.personnel.vo;

import java.time.LocalDateTime;

public record AccountReviewVO(
    String userName,
    String auditStatus,
    String userStatus,
    String auditReason,
    LocalDateTime reviewedAt
) {}
