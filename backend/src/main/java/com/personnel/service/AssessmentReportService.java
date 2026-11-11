package com.personnel.service;

import com.personnel.vo.*;
import java.util.List;

public interface AssessmentReportService {
    AssessmentReportVO generateReport(
        ScaleDefinitionVO scale,
        List<AssessmentDimensionVO> dimensions
    );
}
