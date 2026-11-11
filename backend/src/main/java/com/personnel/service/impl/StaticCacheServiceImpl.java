package com.personnel.service.impl;

import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.StaticCachePolicy;
import com.personnel.config.*;
import com.personnel.service.*;
import com.personnel.vo.StaticCacheStatusVO;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class StaticCacheServiceImpl implements StaticCacheService {

    private final UserService users;
    private final CacheManager manager;
    private final StaticCacheProperties properties;
    private final StaticCachePolicy policy;
    private final AssessmentScaleService scales;
    private final KnowledgeGraphService graphs;
    private final GrowthTemplateService growth;
    private final JobProfileCatalogService jobs;
    private final StaticReferenceService references;

    public StaticCacheServiceImpl(
        UserService users,
        CacheManager manager,
        StaticCacheProperties properties,
        StaticCachePolicy policy,
        AssessmentScaleService scales,
        KnowledgeGraphService graphs,
        GrowthTemplateService growth,
        JobProfileCatalogService jobs,
        StaticReferenceService references
    ) {
        this.users = users;
        this.manager = manager;
        this.properties = properties;
        this.policy = policy;
        this.scales = scales;
        this.graphs = graphs;
        this.growth = growth;
        this.jobs = jobs;
        this.references = references;
    }

    private void requireAdmin(Long actor) {
        var user = users.getById(actor);
        if (
            !"ADMIN".equals(user.roleCode()) ||
            !"ENABLED".equals(user.userStatus()) ||
            !"APPROVED".equals(user.auditStatus())
        ) throw new BusinessException("仅管理员可维护静态缓存");
    }

    @Override
    public StaticCacheStatusVO status(Long actor) {
        requireAdmin(actor);
        return snapshot();
    }

    private StaticCacheStatusVO snapshot() {
        return new StaticCacheStatusVO(
            properties.isEnabled(),
            !properties.isEnabled()
                ? "DISABLED"
                : policy.circuitOpen() ? "MYSQL_FALLBACK" : "READY",
            properties.getTtlSeconds(),
            policy.failures(),
            StaticCacheConfig.NAMES,
            "只缓存发布题库、图谱、岗位规则、建议/提示词模板和系统字典；READY表示允许尝试缓存，不是Redis健康探测。故障自动回源MySQL，30秒冷却后重试；事务内生成报告直接读取MySQL，不缓存答卷或个人报告。"
        );
    }

    @Override
    public StaticCacheStatusVO refresh(Long actor) {
        requireAdmin(actor);
        if (!properties.isEnabled()) return snapshot();
        try {
            for (String name : StaticCacheConfig.NAMES) {
                var cache = manager.getCache(name);
                if (cache != null) cache.clear();
            }
        } catch (RuntimeException exception) {
            policy.failed();
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                "Static catalog refresh failed exception:{}; MySQL fallback active",
                exception.getClass().getSimpleName()
            );
            return snapshot();
        }
        policy.retryNow();
        scales.listPublished();
        graphs.getCurrent();
        growth.getEffective();
        jobs.published();
        references.prompts();
        references.dictionary();
        return snapshot();
    }
}
