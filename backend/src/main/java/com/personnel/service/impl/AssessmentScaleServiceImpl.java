package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AssessmentConstants;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.AssessmentConvert;
import com.personnel.dto.req.*;
import com.personnel.entity.AssessmentScaleDO;
import com.personnel.mapper.AssessmentScaleMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import jakarta.validation.Validator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentScaleServiceImpl implements AssessmentScaleService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentScaleServiceImpl.class);
    private final AssessmentScaleMapper scales;
    private final UserService users;
    private final AssessmentJson json;
    private final Validator validator;
    private final Clock clock;
    private final OperationAuditService audit;

    public AssessmentScaleServiceImpl(
        AssessmentScaleMapper scales,
        UserService users,
        AssessmentJson json,
        Validator validator,
        Clock clock,
        OperationAuditService audit
    ) {
        this.audit = audit;
        this.scales = scales;
        this.users = users;
        this.json = json;
        this.validator = validator;
        this.clock = clock;
    }

    private void requireAdmin(Long id) {
        var user = users.getById(id);
        if (
            !"ADMIN".equals(user.roleCode()) ||
            !"APPROVED".equals(user.auditStatus()) ||
            !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("仅有效管理员可维护量表");
    }

    private AssessmentScaleVO toVO(AssessmentScaleDO value) {
        return AssessmentConvert.toScaleVO(
            value,
            json.read(value.getDefinitionJson(), ScaleDefinitionVO.class)
        );
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(
        cacheNames = "catalog-scales",
        key = "@staticCachePolicy.key('published')",
        condition = "@staticCachePolicy.available()"
    )
    public List<AssessmentScaleVO> listPublished() {
        return scales
            .selectList(
                Wrappers.<AssessmentScaleDO>lambdaQuery()
                    .eq(AssessmentScaleDO::getStatus, AssessmentConstants.PUBLISHED)
                    .orderByDesc(AssessmentScaleDO::getId)
            )
            .stream()
            .map(this::toVO)
            .toList();
    }

    @Override
    public AssessmentScaleVO getPublished(Long id) {
        var value = scales.selectById(id);
        if (
            value == null || !AssessmentConstants.PUBLISHED.equals(value.getStatus())
        ) throw new BusinessException("量表尚未发布或已停用，请刷新列表");
        return toVO(value);
    }

    @Override
    public List<AssessmentScaleVO> listAll(Long adminId) {
        requireAdmin(adminId);
        return scales
            .selectList(
                Wrappers.<AssessmentScaleDO>lambdaQuery().orderByDesc(AssessmentScaleDO::getId)
            )
            .stream()
            .map(this::toVO)
            .toList();
    }

    private void validateDefinition(ScaleImportReqDTO request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new BusinessException(
            "量表资料不完整或不合法：" + violations.iterator().next().getPropertyPath()
        );
        URI uri;
        try {
            uri = URI.create(request.sourceUrl());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("来源链接格式不正确");
        }
        if (
            !List.of("http", "https").contains(uri.getScheme()) ||
            uri.getHost() == null ||
            uri.getUserInfo() != null
        ) throw new BusinessException("来源必须为有效的HTTP/HTTPS链接");
        var keys = new HashSet<String>();
        var counts = new HashMap<String, Integer>();
        for (var question : request.questions()) {
            if (!keys.add(question.key())) throw new BusinessException("题目标识不能重复");
            if (!question.dimension().contains(question.highPole())) throw new BusinessException(
                "计分方向不属于题目维度"
            );
            counts.merge(question.dimension(), 1, Integer::sum);
        }
        for (String dimension : AssessmentConstants.DIMENSIONS) {
            if (counts.getOrDefault(dimension, 0) < 2) throw new BusinessException(
                "每个维度至少需要两道完整题目"
            );
        }
        if (AssessmentConstants.OEJTS_SCORING_VERSION.equals(request.scoringVersion())) {
            if (
                counts
                    .values()
                    .stream()
                    .anyMatch(count -> count != 8)
            ) throw new BusinessException("OpenJung评分版本要求每维8题、共32题");
            var poles = Map.of("EI", "I", "SN", "N", "TF", "T", "JP", "P");
            if (
                request
                    .questions()
                    .stream()
                    .anyMatch(
                        q -> q.weight() != 1 || !poles.get(q.dimension()).equals(q.highPole())
                    )
            ) throw new BusinessException("OpenJung评分版本的方向或权重不一致");
        }
    }

    private String hash(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    content.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentScaleVO importScale(Long adminId, ScaleImportReqDTO request) {
        requireAdmin(adminId);
        validateDefinition(request);
        var definition = AssessmentConvert.toDefinition(request);
        String content = json.write(definition);
        var value = AssessmentConvert.toScaleDO(
            adminId,
            definition,
            content,
            hash(content),
            LocalDateTime.now(clock)
        );
        try {
            scales.insert(value);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("同名同版本量表已存在，请使用新版本号，不覆盖历史内容");
        }
        audit.append(
            AuditOperation.SCALE_IMPORT,
            value.getId(),
            List.of("definitionJson", "status")
        );
        log.info("Assessment scale imported id:{} adminId:{}", value.getId(), adminId);
        return AssessmentConvert.toScaleVO(value, definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-scales",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void publishScale(Long adminId, Long id, ScalePublishReqDTO request) {
        requireAdmin(adminId);
        if (
            !Boolean.TRUE.equals(request.commercialUseVerified()) ||
            !Boolean.TRUE.equals(request.translationVerified()) ||
            !Boolean.TRUE.equals(request.scoringVerified())
        ) throw new BusinessException("发布前必须核验企业使用许可、中文版本及评分规则");
        var value = scales.selectById(id);
        if (value == null) throw new BusinessException("量表不存在");
        var definition = json.read(value.getDefinitionJson(), ScaleDefinitionVO.class);
        String license = definition.license().toUpperCase(Locale.ROOT);
        if (
            license.contains("-NC") ||
            license.contains("NONCOMMERCIAL") ||
            license.contains("NON-COMMERCIAL") ||
            license.contains("非商业")
        ) throw new BusinessException(
            "该许可包含非商业限制，不能发布为企业正式题库；如另获授权，请以新版本导入独立授权资料"
        );
        if (
            scales.publishDraft(id, adminId, LocalDateTime.now(clock)) != 1
        ) throw new BusinessException("仅未发布版本可以发布，已发布内容不可覆盖");
        audit.append(
            AuditOperation.SCALE_PUBLISH,
            id,
            List.of("status", "publishedBy", "publishedAt")
        );
        log.info("Assessment scale published id:{} adminId:{}", id, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(
        cacheNames = "catalog-scales",
        allEntries = true,
        condition = "@staticCachePolicy.enabled()"
    )
    public void archiveScale(Long adminId, Long id) {
        requireAdmin(adminId);
        if (scales.archivePublished(id, LocalDateTime.now(clock)) != 1) throw new BusinessException(
            "仅已发布量表可以停用"
        );
        audit.append(AuditOperation.SCALE_ARCHIVE, id, List.of("status"));
        log.info("Assessment scale archived id:{} adminId:{}", id, adminId);
    }
}
