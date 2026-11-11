package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.SystemSettingsUpdateReqDTO;
import com.personnel.service.SystemSettingsService;
import com.personnel.vo.SystemSettingsVO;
import com.personnel.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system-settings")
public class SystemSettingsController {

    private final SystemSettingsService settings;

    public SystemSettingsController(SystemSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public Result<SystemSettingsVO> getSettings() {
        return Result.success(settings.getCurrent());
    }

    @PutMapping
    public Result<SystemSettingsVO> updateSettings(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody SystemSettingsUpdateReqDTO request
    ) {
        return Result.success(settings.updateSettings(user.id(), request));
    }
}
