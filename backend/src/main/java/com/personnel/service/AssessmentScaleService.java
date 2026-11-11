package com.personnel.service;

import com.personnel.dto.req.*;
import com.personnel.vo.AssessmentScaleVO;
import java.util.List;

public interface AssessmentScaleService {
    List<AssessmentScaleVO> listPublished();
    AssessmentScaleVO getPublished(Long id);
    List<AssessmentScaleVO> listAll(Long adminId);
    AssessmentScaleVO importScale(Long adminId, ScaleImportReqDTO request);
    void publishScale(Long adminId, Long id, ScalePublishReqDTO request);
    void archiveScale(Long adminId, Long id);
}
