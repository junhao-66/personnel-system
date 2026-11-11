package com.personnel.vo;

import java.util.List;

public record StaticCacheStatusVO(
    boolean enabled,
    String state,
    int ttlSeconds,
    long failureCount,
    List<String> catalogs,
    String notice
) {}
