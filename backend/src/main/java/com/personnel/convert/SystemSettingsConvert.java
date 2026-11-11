package com.personnel.convert;

import com.personnel.entity.SystemSettingsDO;
import com.personnel.vo.SystemSettingsVO;

public final class SystemSettingsConvert {

    private SystemSettingsConvert() {}

    public static SystemSettingsVO toVO(SystemSettingsDO value) {
        return new SystemSettingsVO(
            Boolean.TRUE.equals(value.getAssessmentEnabled()),
            value.getAssessmentNotice(),
            Boolean.TRUE.equals(value.getAnnouncementEnabled()),
            value.getAnnouncementTitle(),
            value.getAnnouncementContent(),
            value.getRevision(),
            value.getUpdateTime()
        );
    }
}
