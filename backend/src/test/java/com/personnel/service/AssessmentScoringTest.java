package com.personnel.service;

import com.personnel.AssessmentFixtures;
import com.personnel.common.exception.BusinessException;
import com.personnel.service.impl.AssessmentScoringServiceImpl;
import com.personnel.vo.AssessmentDimensionVO;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;

class AssessmentScoringTest {
    AssessmentScoringService scorer = new AssessmentScoringServiceImpl();
    @TestFactory Stream<DynamicTest> allSixteenTypesWithWeightedAndReverseItems() {
        return IntStream.range(0, 16).mapToObj(mask -> {
            String type = "" + ((mask & 8) == 0 ? 'E' : 'I') + ((mask & 4) == 0 ? 'S' : 'N') + ((mask & 2) == 0 ? 'T' : 'F') + ((mask & 1) == 0 ? 'J' : 'P');
            return DynamicTest.dynamicTest(type, () -> {
                var scores = scorer.score(AssessmentFixtures.definition(), AssessmentFixtures.answers(type));
                assertThat(scores.stream().map(AssessmentDimensionVO::preferredPole).collect(Collectors.joining())).isEqualTo(type);
                for (var dimension : scores) {
                    assertThat(dimension.firstPercent().add(dimension.secondPercent())).isEqualByComparingTo("100");
                    assertThat(dimension.firstPercent()).isIn(new BigDecimal("100.00"), new BigDecimal("0.00"));
                    assertThat(dimension.minimumScore()).isEqualTo(3); assertThat(dimension.maximumScore()).isEqualTo(15);
                    assertThat(dimension.balanced()).isFalse(); assertThat(dimension.weakPreference()).isFalse();
                }
            });
        });
    }
    @Test void midpointIsMarkedBalancedAndUsesDocumentedFixedTieConvention() {
        var answers = AssessmentFixtures.answers("ESTJ"); answers.replaceAll((key, value) -> 3);
        var values = scorer.score(AssessmentFixtures.definition(), answers);
        assertThat(values.stream().map(AssessmentDimensionVO::preferredPole).collect(Collectors.joining())).isEqualTo("ESTJ");
        assertThat(values).allSatisfy(value -> { assertThat(value.balanced()).isTrue(); assertThat(value.weakPreference()).isTrue(); assertThat(value.firstPercent()).isEqualByComparingTo("50"); });
    }
    @Test void missingAnswersAreAllowedInDraftButCannotBeScored() {
        scorer.validateAnswers(AssessmentFixtures.definition(), Map.of("EI1", 2), false);
        assertThatThrownBy(() -> scorer.score(AssessmentFixtures.definition(), Map.of("EI1", 2))).hasMessageContaining("全部必答题");
    }
    @Test void unknownOutOfRangeAndNullAnswersAreRejected() {
        assertThatThrownBy(() -> scorer.validateAnswers(AssessmentFixtures.definition(), Map.of("injected", 3), false)).hasMessageContaining("不属于");
        assertThatThrownBy(() -> scorer.validateAnswers(AssessmentFixtures.definition(), Map.of("EI1", 6), false)).hasMessageContaining("1至5");
        var answers = new HashMap<String, Integer>(); answers.put("EI1", null);
        assertThatThrownBy(() -> scorer.validateAnswers(AssessmentFixtures.definition(), answers, false)).isInstanceOf(BusinessException.class);
    }
    @Test void leftAndRightOrientationCannotBeReplacedByClientTypeClaims() {
        var answers = AssessmentFixtures.answers("ESTJ"); answers.put("EI2", 5);
        var ei = scorer.score(AssessmentFixtures.definition(), answers).get(0);
        assertThat(ei.rawFirstScore()).isEqualTo(11); assertThat(ei.firstPercent()).isEqualByComparingTo("66.67");
        assertThat(ei.secondPercent()).isEqualByComparingTo("33.33");
    }
}
