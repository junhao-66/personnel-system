package com.personnel.vo;

public record GrowthTemplateVO(
    Long id,
    String version,
    String title,
    String status,
    String contentHash,
    String suggestedCycle,
    String observationTarget,
    String reviewPrompt,
    String fallbackContext,
    String fallbackAction
) {}
