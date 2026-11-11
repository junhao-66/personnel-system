package com.personnel.service.impl;

import com.personnel.common.constant.AssessmentConstants;
import com.personnel.common.exception.BusinessException;
import com.personnel.service.AssessmentScoringService;
import com.personnel.vo.*;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Supported algorithm is explicit; importing a different instrument requires its own verified scorer. */
@Service
public class AssessmentScoringServiceImpl implements AssessmentScoringService {

    @Override
    public void validateAnswers(
        ScaleDefinitionVO scale,
        Map<String, Integer> answers,
        boolean complete
    ) {
        if (answers == null) throw new BusinessException("答案不能为空");
        var keys = scale.questions().stream().map(ScaleQuestionVO::key).collect(Collectors.toSet());
        if (!keys.containsAll(answers.keySet())) throw new BusinessException(
            "答案包含不属于本次量表的题目"
        );
        if (
            answers
                .values()
                .stream()
                .anyMatch(v -> v == null || v < 1 || v > 5)
        ) throw new BusinessException("每道题的答案必须是1至5的整数");
        if (complete && !answers.keySet().equals(keys)) throw new BusinessException(
            "请完成全部必答题后再提交"
        );
    }

    @Override
    public List<AssessmentDimensionVO> score(
        ScaleDefinitionVO scale,
        Map<String, Integer> answers
    ) {
        validateAnswers(scale, answers, true);
        if (
            !AssessmentConstants.SCORING_VERSION.equals(scale.scoringVersion()) &&
            !AssessmentConstants.OEJTS_SCORING_VERSION.equals(scale.scoringVersion())
        ) throw new BusinessException("该评分版本不受支持");
        return AssessmentConstants.DIMENSIONS.stream()
            .map(dimension -> scoreDimension(dimension, scale, answers))
            .toList();
    }

    private AssessmentDimensionVO scoreDimension(
        String dimension,
        ScaleDefinitionVO scale,
        Map<String, Integer> answers
    ) {
        var questions = scale
            .questions()
            .stream()
            .filter(q -> dimension.equals(q.dimension()))
            .toList();
        int totalWeight = questions.stream().mapToInt(ScaleQuestionVO::weight).sum();
        if (totalWeight == 0) throw new BusinessException("量表维度不完整");
        int raw = questions
            .stream()
            .mapToInt(
                q ->
                    (q.highPole().equals(dimension.substring(0, 1))
                            ? answers.get(q.key())
                            : 6 - answers.get(q.key())) *
                    q.weight()
            )
            .sum();
        int offset = raw - totalWeight;
        int range = totalWeight * 4;
        BigDecimal first = BigDecimal.valueOf(offset * 100L).divide(
            BigDecimal.valueOf(range),
            2,
            RoundingMode.HALF_UP
        );
        BigDecimal second = BigDecimal.valueOf(100).subtract(first);
        // Tie classification uses a documented fixed convention; weakness/ties rely on raw values, not rounded percentages.
        boolean balanced = offset * 2 == range;
        boolean weak = Math.abs(offset * 2 - range) * 10L <= range;
        String preferred = dimension.substring(
            offset * 2 >= range ? 0 : 1,
            offset * 2 >= range ? 1 : 2
        );
        // OpenJung normalizes TF as F->T. Its exact midpoint resolves to F, unlike our original demo convention.
        if (
            balanced &&
            "TF".equals(dimension) &&
            AssessmentConstants.OEJTS_SCORING_VERSION.equals(scale.scoringVersion())
        ) preferred = "F";
        return new AssessmentDimensionVO(
            dimension,
            raw,
            totalWeight,
            totalWeight * 5,
            first,
            second,
            preferred,
            balanced,
            weak
        );
    }
}
