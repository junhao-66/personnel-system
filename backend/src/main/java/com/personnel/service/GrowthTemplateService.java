package com.personnel.service;

import com.personnel.dto.req.GrowthTemplateCreateReqDTO;
import com.personnel.vo.GrowthTemplateVO;
import java.util.List;

public interface GrowthTemplateService {
    List<GrowthTemplateVO> listVersions(Long actor);
    GrowthTemplateVO getEffective();
    GrowthTemplateVO createVersion(Long actor, GrowthTemplateCreateReqDTO request);
    void publishVersion(Long actor, Long id);
    void archiveVersion(Long actor, Long id);
}
