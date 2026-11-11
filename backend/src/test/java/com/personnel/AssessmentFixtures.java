package com.personnel;

import com.personnel.common.constant.AssessmentConstants;
import com.personnel.convert.AssessmentConvert;
import com.personnel.dto.req.*;
import com.personnel.vo.ScaleDefinitionVO;
import java.time.LocalDate;
import java.util.*;

/** Original synthetic fixtures for software tests only. Not a psychometric instrument; never seed production. */
public final class AssessmentFixtures {
    private AssessmentFixtures() { }
    public static ScaleImportReqDTO scale(String version, String license) {
        var questions = new ArrayList<ScaleQuestionReqDTO>();
        for (String dimension : AssessmentConstants.DIMENSIONS) {
            questions.add(new ScaleQuestionReqDTO(dimension + "1", "测试左端一", "测试右端一", dimension, dimension.substring(0, 1), 2));
            questions.add(new ScaleQuestionReqDTO(dimension + "2", "测试左端二", "测试右端二", dimension, dimension.substring(1, 2), 1));
        }
        return new ScaleImportReqDTO("TEST-ONLY-SYNTHETIC", version, "Original software test fixtures", "Project tests",
                "https://example.org/test-fixtures", LocalDate.of(2026, 1, 1), license, "Synthetic original test inputs, not an enterprise instrument",
                "TEST-ZH-1", "Original synthetic labels, tests only", AssessmentConstants.SCORING_VERSION, "Software test specification only",
                "Rollback or scoped test cleanup; tests only", "test@example.invalid", questions);
    }
    public static ScaleDefinitionVO definition() { return AssessmentConvert.toDefinition(scale("test-v1", "ORIGINAL-TEST-FIXTURE")); }
    public static Map<String, Integer> answers(String typeCode) {
        var result = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < 4; index++) {
            String dimension = AssessmentConstants.DIMENSIONS.get(index);
            boolean first = typeCode.charAt(index) == dimension.charAt(0);
            result.put(dimension + "1", first ? 5 : 1); result.put(dimension + "2", first ? 1 : 5);
        }
        return result;
    }
}
