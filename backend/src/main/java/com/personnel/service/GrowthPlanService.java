package com.personnel.service;

import com.personnel.vo.GraphExplanationVO;
import com.personnel.vo.GrowthPlanVO;
import java.util.List;

public interface GrowthPlanService {
    List<GrowthPlanVO> generatePlans(GraphExplanationVO explanation);
}
