package com.personnel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.exception.BusinessException;
import com.personnel.convert.SystemSettingsConvert;
import com.personnel.dto.req.SystemSettingsUpdateReqDTO;
import com.personnel.entity.SystemSettingsDO;
import com.personnel.mapper.SystemSettingsMapper;
import com.personnel.service.*;
import com.personnel.vo.SystemSettingsVO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private final SystemSettingsMapper mapper;
    private final UserService users;
    private final OperationAuditService audit;
    private final Clock clock;

    public SystemSettingsServiceImpl(
        SystemSettingsMapper mapper,
        UserService users,
        OperationAuditService audit,
        Clock clock
    ) {
        this.mapper = mapper;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    private SystemSettingsDO requireSettings(SystemSettingsDO value) {
        if (value == null) throw new BusinessException("系统配置尚未初始化，请联系管理员");
        return value;
    }

    @Override
    public SystemSettingsVO getCurrent() {
        return SystemSettingsConvert.toVO(requireSettings(mapper.selectById(1L)));
    }

    @Override
    public SystemSettingsVO getForEmployees() {
        var value = getCurrent();
        if (value.announcementEnabled()) return value;
        return new SystemSettingsVO(
            value.assessmentEnabled(),
            value.assessmentNotice(),
            false,
            "",
            "",
            value.revision(),
            value.updatedAt()
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void requireAssessmentOpen() {
        // Share the same row lock as administrator updates: no new draft may slip past a committed closure.
        var value = requireSettings(mapper.selectForUpdate());
        if (!Boolean.TRUE.equals(value.getAssessmentEnabled())) throw new BusinessException(
            "暂未开放新测评：" + value.getAssessmentNotice()
        );
    }

    private String text(String value, int maximum) {
        if (value == null || value.length() > maximum) throw new BusinessException(
            "配置文字长度不合法"
        );
        return value.trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemSettingsVO updateSettings(Long actorId, SystemSettingsUpdateReqDTO request) {
        var actor = users.getById(actorId);
        if (
            !"ADMIN".equals(actor.roleCode()) ||
            !"APPROVED".equals(actor.auditStatus()) ||
            !"ENABLED".equals(actor.userStatus())
        ) {
            throw new BusinessException("仅有效管理员可修改系统配置");
        }
        if (
            request.assessmentEnabled() == null ||
            request.announcementEnabled() == null ||
            request.revision() == null ||
            request.revision() < 0
        ) {
            throw new BusinessException("配置开关与版本不能为空");
        }
        String notice = text(request.assessmentNotice(), 500);
        String title = text(request.announcementTitle(), 100);
        String content = text(request.announcementContent(), 2000);
        if (!request.assessmentEnabled() && notice.isBlank()) throw new BusinessException(
            "暂停新测评时请填写说明"
        );
        if (
            request.announcementEnabled() && (title.isBlank() || content.isBlank())
        ) throw new BusinessException("发布公告时请填写标题和内容");
        var value = requireSettings(mapper.selectForUpdate());
        if (!Objects.equals(value.getRevision(), request.revision())) throw new BusinessException(
            "配置已被其他管理员更新，请重新加载后修改"
        );
        var changed = new ArrayList<String>();
        if (!request.assessmentEnabled().equals(value.getAssessmentEnabled())) changed.add(
            "assessmentEnabled"
        );
        if (!notice.equals(value.getAssessmentNotice())) changed.add("assessmentNotice");
        if (!request.announcementEnabled().equals(value.getAnnouncementEnabled())) changed.add(
            "announcementEnabled"
        );
        if (!title.equals(value.getAnnouncementTitle())) changed.add("announcementTitle");
        if (!content.equals(value.getAnnouncementContent())) changed.add("announcementContent");
        if (changed.isEmpty()) return SystemSettingsConvert.toVO(value);
        LocalDateTime now = LocalDateTime.now(clock);
        mapper.update(
            null,
            Wrappers.<SystemSettingsDO>lambdaUpdate()
                .eq(SystemSettingsDO::getId, 1L)
                .set(SystemSettingsDO::getAssessmentEnabled, request.assessmentEnabled())
                .set(SystemSettingsDO::getAssessmentNotice, notice)
                .set(SystemSettingsDO::getAnnouncementEnabled, request.announcementEnabled())
                .set(SystemSettingsDO::getAnnouncementTitle, title)
                .set(SystemSettingsDO::getAnnouncementContent, content)
                .set(SystemSettingsDO::getRevision, value.getRevision() + 1)
                .set(SystemSettingsDO::getUpdateBy, actorId)
                .set(SystemSettingsDO::getUpdateTime, now)
        );
        audit.append(AuditOperation.SYSTEM_SETTINGS_UPDATE, 1L, changed);
        return getCurrent();
    }
}
