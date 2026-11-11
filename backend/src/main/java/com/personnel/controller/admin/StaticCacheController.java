package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.service.StaticCacheService;
import com.personnel.vo.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/static-cache")
public class StaticCacheController {

    private final StaticCacheService service;

    public StaticCacheController(StaticCacheService service) {
        this.service = service;
    }

    @GetMapping
    public Result<StaticCacheStatusVO> status(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.status(user.id()));
    }

    @PostMapping("/refresh")
    public Result<StaticCacheStatusVO> refresh(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.refresh(user.id()));
    }
}
