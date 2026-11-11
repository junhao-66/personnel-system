package com.personnel.controller.admin;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/assessment")
public class AssessmentAdminController {

    private final AssessmentScaleService scales;
    private final AssessmentService assessments;

    public AssessmentAdminController(AssessmentScaleService scales, AssessmentService assessments) {
        this.scales = scales;
        this.assessments = assessments;
    }

    @GetMapping("/scales")
    public Result<List<AssessmentScaleVO>> listScales(
        @RequestAttribute("currentUser") UserVO user
    ) {
        return Result.success(scales.listAll(user.id()));
    }

    @PostMapping("/scales")
    public Result<AssessmentScaleVO> importScale(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody ScaleImportReqDTO request
    ) {
        return Result.success(scales.importScale(user.id(), request));
    }

    @PutMapping("/scales/{id}/publish")
    public Result<Void> publishScale(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id,
        @Valid @RequestBody ScalePublishReqDTO request
    ) {
        scales.publishScale(user.id(), id, request);
        return Result.success();
    }

    @PutMapping("/scales/{id}/archive")
    public Result<Void> archiveScale(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        scales.archiveScale(user.id(), id);
        return Result.success();
    }

    @GetMapping("/users/{userId}/history")
    public Result<PageResult<AssessmentHistoryVO>> listHistory(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long userId,
        @Valid @ModelAttribute AssessmentQueryReqDTO query
    ) {
        return Result.success(assessments.listHistory(user.id(), userId, query));
    }
}
