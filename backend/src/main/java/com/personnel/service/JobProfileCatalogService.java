package com.personnel.service;

import com.personnel.vo.JobProfileVO;
import java.util.List;

public interface JobProfileCatalogService {
    List<JobProfileVO> published();
}
