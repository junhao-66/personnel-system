package com.personnel.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_system_settings")
public class SystemSettingsDO {

    @TableId
    private Long id;

    private Boolean assessmentEnabled;
    private String assessmentNotice;
    private Boolean announcementEnabled;
    private String announcementTitle;
    private String announcementContent;
    private Integer revision;
    private Long updateBy;
    private LocalDateTime updateTime;
}
