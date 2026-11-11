package com.personnel.controller.assessment;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    private final AssessmentService service;
    private final AssessmentScaleService scales;

    public AssessmentController(AssessmentService service, AssessmentScaleService scales) {
        this.service = service;
        this.scales = scales;
    }

    @GetMapping("/scales")
    public Result<List<AssessmentScaleVO>> listScales() {
        return Result.success(scales.listPublished());
    }

    @GetMapping("/current")
    public Result<AssessmentSessionVO> getCurrent(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.getCurrent(user.id()));
    }

    @PostMapping("/sessions")
    public Result<AssessmentSessionVO> startAssessment(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody AssessmentStartReqDTO request
    ) {
        return Result.success(service.startAssessment(user.id(), request));
    }

    @GetMapping("/sessions/{id}")
    public Result<AssessmentSessionVO> getSession(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        return Result.success(service.getSession(user.id(), id));
    }

    @PutMapping("/sessions/{id}/answers")
    public Result<AssessmentSessionVO> saveAnswers(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id,
        @Valid @RequestBody AssessmentAnswersReqDTO request
    ) {
        return Result.success(service.saveAnswers(user.id(), id, request));
    }

    @PostMapping("/sessions/{id}/submit")
    public Result<AssessmentResultVO> submitAssessment(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id,
        @Valid @RequestBody AssessmentAnswersReqDTO request
    ) {
        return Result.success(service.submitAssessment(user.id(), id, request));
    }

    @GetMapping("/results/{id}")
    public Result<AssessmentResultVO> getResult(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        return Result.success(service.getResult(user.id(), id));
    }

    @GetMapping("/history")
    public Result<PageResult<AssessmentHistoryVO>> listHistory(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute AssessmentQueryReqDTO query
    ) {
        return Result.success(service.listHistory(user.id(), user.id(), query));
    }

    @GetMapping("/compare")
    public Result<AssessmentComparisonVO> compare(
        @RequestAttribute("currentUser") UserVO user,
        @RequestParam Long firstId,
        @RequestParam Long secondId
    ) {
        return Result.success(service.compareResults(user.id(), firstId, secondId));
    }
}
