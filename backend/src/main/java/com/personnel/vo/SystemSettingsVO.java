package com.personnel.vo;

import java.time.LocalDateTime;

public record SystemSettingsVO(
    boolean assessmentEnabled,
    String assessmentNotice,
    boolean announcementEnabled,
    String announcementTitle,
    String announcementContent,
    int revision,
    LocalDateTime updatedAt
) {}
