package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.GrowthTemplateConvert;
import com.personnel.dto.req.GrowthTemplateCreateReqDTO;
import com.personnel.entity.GrowthTemplateDO;
import com.personnel.mapper.GrowthTemplateMapper;
import com.personnel.service.*;
import com.personnel.vo.GrowthTemplateVO;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthTemplateServiceImpl implements GrowthTemplateService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
        GrowthTemplateServiceImpl.class
    );
    private final GrowthTemplateMapper mapper;
    private final UserService users;
    private final OperationAuditService audit;
    private final Validator validator;
    private final AssessmentJson json;
    private final Clock clock;

    public GrowthTemplateServiceImpl(
        GrowthTemplateMapper mapper,
        UserService users,
        OperationAuditService audit,
        Validator validator,
        AssessmentJson json,
        Clock clock
    ) {
        this.mapper = mapper;
        this.users = users;
        this.audit = audit;
        this.validator = validator;
        this.json = json;
        this.clock = clock;
    }

    private void requireAdmin(Long actor) {
        var user = users.getById(actor);
        if (
            !"ADMIN".equals(user.roleCode()) ||
            !"APPROVED".equals(user.auditStatus()) ||
            !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("仅有效管理员可维护建议模板");
    }

    @Override
    public List<GrowthTemplateVO> listVersions(Long actor) {
        requireAdmin(actor);
        return mapper
            .selectList(
                Wrappers.<GrowthTemplateDO>lambdaQuery().orderByDesc(GrowthTemplateDO::getId)
            )
            .stream()
            .map(GrowthTemplateConvert::toVO)
            .toList();
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(
        cacheNames = "catalog-growth",
        key = "@staticCachePolicy.key('effective')",
        condition = "@staticCachePolicy.available()"
    )
    public GrowthTemplateVO getEffective() {
        var rows = mapper.selectList(
            Wrappers.<GrowthTemplateDO>lambdaQuery()
                .eq(GrowthTemplateDO::getStatus, "PUBLISHED")
                .orderByDesc(GrowthTemplateDO::getId)
                .last("LIMIT 1")
        );
        return rows.isEmpty()
            ? GrowthTemplateConvert.builtIn()
            : GrowthTemplateConvert.toVO(rows.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrowthTemplateVO createVersion(Long actor, GrowthTemplateCreateReqDTO request) {
        requireAdmin(actor);
        if (request == null || !validator.validate(request).isEmpty()) throw new BusinessException(
            "请完整填写有效的模板内容"
        );
        if ("BUILTIN_V1".equalsIgnoreCase(request.version())) throw new BusinessException(
            "内置版本号不可使用"
        );
        var normalized = GrowthTemplateConvert.normalize(request);
        String hash;
        try {
            hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    json.write(normalized).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        var row = GrowthTemplateConvert.toDO(normalized, hash, actor, LocalDateTime.now(clock));
        try {
            if (mapper.insert(row) != 1) throw new IllegalStateException("模板保存失败");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("版本号已存在，请使用新版本号");
        }
        audit.append(
            AuditOperation.TEMPLATE_CREATE,
            row.getId(),
            List.of(
                "version",
                "title",
                "suggestedCycle",
                "observationTarget",
                "reviewPrompt",
                "fallbackContext",
                "fallbackAction",
                "status"
            )
        );
        log.info("Growth template created id:{} adminId:{}", row.getId(), actor);
        return GrowthTemplateConvert.toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-growth",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void publishVersion(Long actor, Long id) {
        requireAdmin(actor);
        if (
            mapper.update(
                null,
                Wrappers.<GrowthTemplateDO>lambdaUpdate()
                    .eq(GrowthTemplateDO::getId, id)
                    .eq(GrowthTemplateDO::getStatus, "DRAFT")
                    .set(GrowthTemplateDO::getStatus, "PUBLISHED")
            ) !=
            1
        ) throw new BusinessException("仅草稿模板可以发布");
        audit.append(AuditOperation.TEMPLATE_PUBLISH, id, List.of("status"));
        log.info("Growth template published id:{} adminId:{}", id, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-growth",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void archiveVersion(Long actor, Long id) {
        requireAdmin(actor);
        if (
            mapper.update(
                null,
                Wrappers.<GrowthTemplateDO>lambdaUpdate()
                    .eq(GrowthTemplateDO::getId, id)
                    .eq(GrowthTemplateDO::getStatus, "PUBLISHED")
                    .set(GrowthTemplateDO::getStatus, "ARCHIVED")
            ) !=
            1
        ) throw new BusinessException("仅已发布模板可以停用");
        audit.append(AuditOperation.TEMPLATE_ARCHIVE, id, List.of("status"));
        log.info("Growth template archived id:{} adminId:{}", id, actor);
    }
}
