package com.personnel.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record GraphImportReqDTO(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String version,
    @NotBlank @Size(max = 100) String title,
    @NotNull @Size(min = 1, max = 32) List<@NotNull @Valid Source> sources,
    @NotNull @Size(min = 1, max = 128) List<@NotNull @Valid Node> nodes,
    @NotNull @Size(min = 1, max = 256) List<@NotNull @Valid Edge> edges,
    @NotNull @Size(max = 32) List<@NotNull @Valid Rule> rules
) {
    public record Source(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String id,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100) String author,
        @NotBlank @Size(max = 500) String url,
        @NotNull @PastOrPresent LocalDate accessedDate,
        @NotBlank @Size(max = 200) String license,
        @NotBlank @Size(max = 1000) String attribution,
        @NotBlank @Size(max = 100) String contentVersion,
        @NotBlank @Pattern(regexp = "PROJECT_DEMO|CURATED_REFERENCE") String evidenceLevel
    ) {}

    public record Node(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String id,
        @NotBlank @Pattern(
            regexp = "QUESTION|DIMENSION|TYPE|PREFERENCE|TENDENCY|WORK_STYLE|DEVELOPMENT|ACTION|CONTEXT|SOURCE"
        ) String type,
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank String sourceId
    ) {}

    public record Edge(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String id,
        @NotBlank String from,
        @NotBlank String to,
        @NotBlank @Pattern(
            regexp = "ASSOCIATED_WITH|SUGGESTS|IN_CONTEXT|SUPPORTED_BY|MEASURES"
        ) String relation,
        @NotBlank @Size(max = 500) String explanation,
        @NotBlank String sourceId
    ) {}

    public record Condition(
        @NotBlank String nodeId,
        @NotBlank @Pattern(regexp = "EI|SN|TF|JP") String dimension,
        @NotBlank @Pattern(regexp = "[EISNTFJP]") String pole,
        @NotNull @Min(56) @Max(100) Integer minimumPercent
    ) {}

    public record Rule(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String id,
        @NotBlank String actionId,
        @NotBlank String contextId,
        @NotBlank @Size(max = 500) String explanation,
        @NotNull @Size(min = 2, max = 4) List<@NotNull @Valid Condition> conditions,
        @NotNull @Size(min = 2, max = 32) List<@NotBlank String> edgeIds
    ) {}
}
