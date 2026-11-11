package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personnel.common.base.PageResult;
import com.personnel.common.constant.AssessmentConstants;
import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AssessmentJson;
import com.personnel.convert.AssessmentConvert;
import com.personnel.dto.req.*;
import com.personnel.entity.AssessmentSessionDO;
import com.personnel.mapper.AssessmentSessionMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentServiceImpl implements AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentServiceImpl.class);
    private final AssessmentSessionMapper sessions;
    private final AssessmentScaleService scales;
    private final AssessmentScoringService scoring;
    private final AssessmentReportService reports;
    private final UserService users;
    private final AssessmentJson json;
    private final Clock clock;
    private final SystemSettingsService settings;

    public AssessmentServiceImpl(
        AssessmentSessionMapper sessions,
        AssessmentScaleService scales,
        AssessmentScoringService scoring,
        AssessmentReportService reports,
        UserService users,
        AssessmentJson json,
        Clock clock,
        SystemSettingsService settings
    ) {
        this.sessions = sessions;
        this.scales = scales;
        this.scoring = scoring;
        this.reports = reports;
        this.users = users;
        this.json = json;
        this.clock = clock;
        this.settings = settings;
    }

    private UserVO requireEnabled(Long id) {
        var user = users.getById(id);
        if (
            !"APPROVED".equals(user.auditStatus()) || !"ENABLED".equals(user.userStatus())
        ) throw new BusinessException("账号未审核通过或已停用");
        return user;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private AssessmentSessionDO findDraft(Long userId) {
        return sessions.selectOne(
            Wrappers.<AssessmentSessionDO>lambdaQuery()
                .eq(AssessmentSessionDO::getUserId, userId)
                .eq(AssessmentSessionDO::getStatus, AssessmentConstants.DRAFT)
        );
    }

    private AssessmentSessionVO toVO(AssessmentSessionDO value) {
        return AssessmentConvert.toSessionVO(
            value,
            json.read(value.getScaleSnapshot(), ScaleDefinitionVO.class),
            json.readAnswers(value.getAnswersJson())
        );
    }

    private AssessmentSessionDO requireOwned(Long userId, Long id, boolean lock) {
        var value = lock ? sessions.selectForUpdate(id) : sessions.selectById(id);
        if (value == null || !value.getUserId().equals(userId)) throw new BusinessException(
            "测评记录不存在或无权访问"
        );
        return value;
    }

    @Override
    public AssessmentSessionVO getCurrent(Long userId) {
        requireEnabled(userId);
        var value = findDraft(userId);
        return value == null ? null : toVO(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentSessionVO startAssessment(Long userId, AssessmentStartReqDTO request) {
        if (!Boolean.TRUE.equals(request.consentAccepted())) throw new BusinessException(
            "请先阅读并同意测评及数据使用说明"
        );
        // Serialize creation per employee, so double-clicks or simultaneous tabs cannot create multiple drafts.
        users.lockUser(userId);
        requireEnabled(userId);
        var existing = findDraft(userId);
        if (existing != null) {
            if (!existing.getScaleId().equals(request.scaleId())) throw new BusinessException(
                "您已有未完成测评，请先继续完成原答卷"
            );
            return toVO(existing);
        }
        settings.requireAssessmentOpen();
        var scale = scales.getPublished(request.scaleId());
        var value = AssessmentConvert.toSessionDO(
            userId,
            scale,
            json.write(scale.definition()),
            now()
        );
        sessions.insert(value);
        log.info(
            "Assessment started id:{} userId:{} scaleId:{}",
            value.getId(),
            userId,
            scale.id()
        );
        return toVO(value);
    }

    @Override
    public AssessmentSessionVO getSession(Long userId, Long id) {
        requireEnabled(userId);
        return toVO(requireOwned(userId, id, false));
    }

    private void requireRevision(AssessmentSessionDO value, AssessmentAnswersReqDTO request) {
        if (!Objects.equals(value.getRevision(), request.revision())) throw new BusinessException(
            "答卷已在其他窗口保存，或上次请求已成功。请重新加载服务器进度，避免覆盖答案"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentSessionVO saveAnswers(Long userId, Long id, AssessmentAnswersReqDTO request) {
        requireEnabled(userId);
        var value = requireOwned(userId, id, true);
        if (!AssessmentConstants.DRAFT.equals(value.getStatus())) throw new BusinessException(
            "已提交的测评不可修改，请开始新的测评"
        );
        requireRevision(value, request);
        var scale = json.read(value.getScaleSnapshot(), ScaleDefinitionVO.class);
        scoring.validateAnswers(scale, request.answers(), false);
        String answers = json.write(request.answers());
        LocalDateTime time = now();
        if (
            sessions.saveDraft(id, request.revision(), answers, time) != 1
        ) throw new BusinessException("进度已变化，请重新加载服务器进度");
        value.setAnswersJson(answers);
        value.setRevision(value.getRevision() + 1);
        value.setUpdateTime(time);
        return toVO(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentResultVO submitAssessment(
        Long userId,
        Long id,
        AssessmentAnswersReqDTO request
    ) {
        requireEnabled(userId);
        var value = requireOwned(userId, id, true);
        if (AssessmentConstants.COMPLETED.equals(value.getStatus())) return json.read(
            value.getResultJson(),
            AssessmentResultVO.class
        );
        requireRevision(value, request);
        var scale = json.read(value.getScaleSnapshot(), ScaleDefinitionVO.class);
        var dimensions = scoring.score(scale, request.answers());
        String type = dimensions
            .stream()
            .map(AssessmentDimensionVO::preferredPole)
            .collect(Collectors.joining());
        LocalDateTime time = now();
        var result = new AssessmentResultVO(
            value.getId(),
            userId,
            scale.name(),
            scale.version(),
            scale.scoringVersion(),
            value.getContentHash(),
            type,
            time,
            dimensions,
            reports.generateReport(scale, dimensions),
            scale
        );
        if (
            sessions.completeDraft(
                id,
                request.revision(),
                json.write(request.answers()),
                json.write(result),
                time
            ) !=
            1
        ) throw new BusinessException("测评状态已变化，请刷新查看结果");
        // Answers, scores, source/version and report text commit together; later template changes do not rewrite history.
        log.info("Assessment completed id:{} userId:{}", id, userId);
        return result;
    }

    @Override
    public AssessmentResultVO getResult(Long userId, Long id) {
        var actor = requireEnabled(userId);
        var value = sessions.selectById(id);
        if (
            value == null ||
            (!value.getUserId().equals(userId) && !"ADMIN".equals(actor.roleCode()))
        ) throw new BusinessException("测评记录不存在或无权访问");
        if (!AssessmentConstants.COMPLETED.equals(value.getStatus())) throw new BusinessException(
            "该测评尚未提交，没有结果报告"
        );
        return json.read(value.getResultJson(), AssessmentResultVO.class);
    }

    @Override
    public PageResult<AssessmentHistoryVO> listHistory(
        Long actorId,
        Long ownerId,
        AssessmentQueryReqDTO query
    ) {
        var actor = requireEnabled(actorId);
        if (
            !actorId.equals(ownerId) && !"ADMIN".equals(actor.roleCode())
        ) throw new BusinessException("无权查看其他员工的测评历史");
        var page = sessions.selectPage(
            new Page<>(query.getPageNum(), query.getPageSize()),
            Wrappers.<AssessmentSessionDO>lambdaQuery()
                .select(AssessmentSessionDO::getId, AssessmentSessionDO::getResultJson)
                .eq(AssessmentSessionDO::getUserId, ownerId)
                .eq(AssessmentSessionDO::getStatus, AssessmentConstants.COMPLETED)
                .orderByDesc(AssessmentSessionDO::getSubmittedAt)
                .orderByDesc(AssessmentSessionDO::getId)
        );
        var result = page
            .getRecords()
            .stream()
            .map(r ->
                AssessmentConvert.toHistoryVO(
                    json.read(r.getResultJson(), AssessmentResultVO.class)
                )
            )
            .toList();
        return new PageResult<>(result, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AssessmentComparisonVO compareResults(Long userId, Long firstId, Long secondId) {
        if (Objects.equals(firstId, secondId)) throw new BusinessException(
            "请选择两次不同的测评记录"
        );
        var first = getResult(userId, firstId);
        var second = getResult(userId, secondId);
        if (!first.userId().equals(second.userId())) throw new BusinessException(
            "只能比较同一员工的历次结果"
        );
        boolean comparable =
            first.contentHash().equals(second.contentHash()) &&
            first.scoringVersion().equals(second.scoringVersion());
        return new AssessmentComparisonVO(
            comparable,
            comparable
                ? "同一量表内容及评分版本，仅展示偏好变化，不代表能力提升或退步。"
                : "量表内容或评分版本不同，不能进行同尺度得分比较。",
            first,
            second
        );
    }
}
