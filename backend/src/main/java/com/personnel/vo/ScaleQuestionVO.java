package com.personnel.vo;

public record ScaleQuestionVO(
    String key,
    String leftText,
    String rightText,
    String dimension,
    String highPole,
    int weight
) {}
