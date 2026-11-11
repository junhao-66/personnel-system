package com.personnel.controller.user;

import com.personnel.common.result.Result;
import com.personnel.service.SystemSettingsService;
import com.personnel.vo.SystemSettingsVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/settings")
public class SystemNoticeController {

    private final SystemSettingsService settings;

    public SystemNoticeController(SystemSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public Result<SystemSettingsVO> getSettings() {
        return Result.success(settings.getForEmployees());
    }
}
