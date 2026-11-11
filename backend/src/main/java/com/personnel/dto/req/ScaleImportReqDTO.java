package com.personnel.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** Every import creates an immutable new version; no update endpoint overwrites prior content. */
public record ScaleImportReqDTO(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String version,
    @NotBlank @Size(max = 100) String sourceTitle,
    @NotBlank @Size(max = 100) String author,
    @NotBlank @Size(max = 500) String sourceUrl,
    @NotNull @PastOrPresent LocalDate accessedDate,
    @NotBlank @Size(max = 100) String license,
    @NotBlank @Size(max = 1000) String licenseEvidence,
    @NotBlank @Size(max = 200) String chineseVersion,
    @NotBlank @Size(max = 1000) String translationEvidence,
    @NotBlank
    @Pattern(regexp = "WEIGHTED_BIPOLAR_1_5_V1|OEJTS_OPENJUNG_32_V1")
    String scoringVersion,
    @NotBlank @Size(max = 1000) String scoringEvidence,
    @NotBlank @Size(max = 500) String retentionPolicy,
    @NotBlank @Size(max = 200) String contact,
    @NotNull @Size(min = 8, max = 128) List<@NotNull @Valid ScaleQuestionReqDTO> questions
) {}
