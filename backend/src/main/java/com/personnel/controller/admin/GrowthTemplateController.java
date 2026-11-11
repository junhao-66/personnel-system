package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.GrowthTemplateCreateReqDTO;
import com.personnel.service.GrowthTemplateService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/growth-templates")
public class GrowthTemplateController {

    private final GrowthTemplateService service;

    public GrowthTemplateController(GrowthTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<GrowthTemplateVO>> list(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.listVersions(user.id()));
    }

    @GetMapping("/effective")
    public Result<GrowthTemplateVO> effective() {
        return Result.success(service.getEffective());
    }

    @PostMapping
    public Result<GrowthTemplateVO> create(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody GrowthTemplateCreateReqDTO request
    ) {
        return Result.success(service.createVersion(user.id(), request));
    }

    @PutMapping("/{id}/publish")
    public Result<Void> publish(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        service.publishVersion(user.id(), id);
        return Result.success();
    }

    @PutMapping("/{id}/archive")
    public Result<Void> archive(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        service.archiveVersion(user.id(), id);
        return Result.success();
    }
}
