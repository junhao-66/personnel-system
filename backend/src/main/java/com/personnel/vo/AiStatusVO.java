package com.personnel.vo;

public record AiStatusVO(
    String configuredMode,
    boolean externalEnabled,
    boolean remoteConfigured,
    String notice
) {}
