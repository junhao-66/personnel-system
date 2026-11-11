package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.JobProfileCreateReqDTO;
import com.personnel.service.JobPreferenceService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/job-preferences")
public class JobPreferenceController {

    private final JobPreferenceService service;

    public JobPreferenceController(JobPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<JobProfileVO>> list(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.listProfiles(user.id()));
    }

    @PostMapping
    public Result<JobProfileVO> create(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody JobProfileCreateReqDTO request
    ) {
        return Result.success(service.createProfile(user.id(), request));
    }

    @PutMapping("/{id}/publish")
    public Result<Void> publish(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        service.publishProfile(user.id(), id);
        return Result.success();
    }

    @PutMapping("/{id}/archive")
    public Result<Void> archive(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        service.archiveProfile(user.id(), id);
        return Result.success();
    }

    @GetMapping("/users/{userId}")
    public Result<JobExplorationVO> explore(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long userId
    ) {
        return Result.success(service.explore(user.id(), userId));
    }
}
