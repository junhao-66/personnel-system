package com.personnel.service;

import com.personnel.dto.req.GraphImportReqDTO;
import com.personnel.vo.*;
import java.util.List;

public interface KnowledgeGraphService {
    KnowledgeGraphVO getCurrent();
    List<KnowledgeGraphVO> listVersions(Long adminId);
    KnowledgeGraphVO importVersion(Long adminId, GraphImportReqDTO request);
    void publishVersion(Long adminId, Long id);
    void archiveVersion(Long adminId, Long id);
    GraphExplanationVO explain(List<AssessmentDimensionVO> dimensions);
}
