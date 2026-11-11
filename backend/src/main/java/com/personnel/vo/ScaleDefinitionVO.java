package com.personnel.vo;

import java.time.LocalDate;
import java.util.List;

public record ScaleDefinitionVO(
    String name,
    String version,
    String sourceTitle,
    String author,
    String sourceUrl,
    LocalDate accessedDate,
    String license,
    String licenseEvidence,
    String chineseVersion,
    String translationEvidence,
    String scoringVersion,
    String scoringEvidence,
    String retentionPolicy,
    String contact,
    List<ScaleQuestionVO> questions
) {}
