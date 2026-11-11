package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.JobPreferenceConvert;
import com.personnel.entity.JobPreferenceProfileDO;
import com.personnel.mapper.JobPreferenceProfileMapper;
import com.personnel.service.JobProfileCatalogService;
import com.personnel.vo.JobProfileVO;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class JobProfileCatalogServiceImpl implements JobProfileCatalogService {

    private final JobPreferenceProfileMapper mapper;
    private final AssessmentJson json;

    public JobProfileCatalogServiceImpl(JobPreferenceProfileMapper mapper, AssessmentJson json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    @Cacheable(
        cacheNames = "catalog-jobs",
        key = "@staticCachePolicy.key('published')",
        condition = "@staticCachePolicy.available()"
    )
    public List<JobProfileVO> published() {
        return mapper
            .selectList(
                Wrappers.<JobPreferenceProfileDO>lambdaQuery()
                    .eq(JobPreferenceProfileDO::getStatus, "PUBLISHED")
                    .orderByDesc(JobPreferenceProfileDO::getId)
            )
            .stream()
            .map(row -> JobPreferenceConvert.toVO(row, json))
            .toList();
    }
}
