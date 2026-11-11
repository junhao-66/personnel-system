package com.personnel.service;

import com.personnel.vo.*;
import java.util.List;
import java.util.Map;

public interface AssessmentScoringService {
    void validateAnswers(ScaleDefinitionVO scale, Map<String, Integer> answers, boolean complete);
    List<AssessmentDimensionVO> score(ScaleDefinitionVO scale, Map<String, Integer> answers);
}
