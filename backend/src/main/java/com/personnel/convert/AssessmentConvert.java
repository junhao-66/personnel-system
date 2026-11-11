package com.personnel.convert;

import com.personnel.common.constant.AssessmentConstants;
import com.personnel.dto.req.ScaleImportReqDTO;
import com.personnel.entity.*;
import com.personnel.vo.*;
import java.time.LocalDateTime;
import java.util.Map;

public final class AssessmentConvert {

    private AssessmentConvert() {}

    public static ScaleDefinitionVO toDefinition(ScaleImportReqDTO dto) {
        return new ScaleDefinitionVO(
            dto.name().trim(),
            dto.version(),
            dto.sourceTitle(),
            dto.author(),
            dto.sourceUrl(),
            dto.accessedDate(),
            dto.license(),
            dto.licenseEvidence(),
            dto.chineseVersion(),
            dto.translationEvidence(),
            dto.scoringVersion(),
            dto.scoringEvidence(),
            dto.retentionPolicy(),
            dto.contact(),
            dto
                .questions()
                .stream()
                .map(q ->
                    new ScaleQuestionVO(
                        q.key(),
                        q.leftText(),
                        q.rightText(),
                        q.dimension(),
                        q.highPole(),
                        q.weight()
                    )
                )
                .toList()
        );
    }

    public static AssessmentScaleDO toScaleDO(
        Long adminId,
        ScaleDefinitionVO definition,
        String content,
        String hash,
        LocalDateTime now
    ) {
        var value = new AssessmentScaleDO();
        value.setEnterpriseId(1L);
        value.setName(definition.name());
        value.setVersion(definition.version());
        value.setStatus(AssessmentConstants.DRAFT);
        value.setDefinitionJson(content);
        value.setContentHash(hash);
        value.setCreateBy(adminId);
        value.setCreateTime(now);
        value.setUpdateTime(now);
        return value;
    }

    public static AssessmentScaleVO toScaleVO(
        AssessmentScaleDO value,
        ScaleDefinitionVO definition
    ) {
        return new AssessmentScaleVO(
            value.getId(),
            value.getStatus(),
            value.getContentHash(),
            definition,
            value.getPublishedBy(),
            value.getPublishedAt()
        );
    }

    public static AssessmentSessionDO toSessionDO(
        Long userId,
        AssessmentScaleVO scale,
        String snapshot,
        LocalDateTime now
    ) {
        var value = new AssessmentSessionDO();
        value.setEnterpriseId(1L);
        value.setUserId(userId);
        value.setScaleId(scale.id());
        value.setScaleSnapshot(snapshot);
        value.setContentHash(scale.contentHash());
        value.setStatus(AssessmentConstants.DRAFT);
        value.setRevision(0);
        value.setAnswersJson("{}");
        value.setConsentAt(now);
        value.setCreateTime(now);
        value.setUpdateTime(now);
        return value;
    }

    public static AssessmentSessionVO toSessionVO(
        AssessmentSessionDO value,
        ScaleDefinitionVO scale,
        Map<String, Integer> answers
    ) {
        return new AssessmentSessionVO(
            value.getId(),
            value.getStatus(),
            value.getRevision(),
            value.getContentHash(),
            scale,
            answers,
            value.getConsentAt(),
            value.getUpdateTime(),
            value.getSubmittedAt()
        );
    }

    public static AssessmentHistoryVO toHistoryVO(AssessmentResultVO value) {
        return new AssessmentHistoryVO(
            value.id(),
            value.scaleName(),
            value.scaleVersion(),
            value.scoringVersion(),
            value.contentHash(),
            value.typeCode(),
            value.submittedAt()
        );
    }
}
