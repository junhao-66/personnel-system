package com.personnel.vo;

import java.util.List;

public record AiAnswerVO(
    String mode,
    String reason,
    String promptVersion,
    String contextVersion,
    Long resultId,
    List<Paragraph> paragraphs,
    String notice
) {
    public record Paragraph(String evidenceId, String text, String basis) {}
}
