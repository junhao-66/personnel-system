package com.personnel.controller.leave;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.*;
import com.personnel.service.LeaveService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public Result<LeaveOptionsVO> getOptions(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.getOptions(user.id()));
    }

    @PostMapping
    public Result<LeaveVO> addLeave(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody LeaveCreateReqDTO request
    ) {
        return Result.success(service.addLeave(user.id(), request));
    }

    @GetMapping
    public Result<PageResult<LeaveVO>> listMine(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute LeaveQueryReqDTO query
    ) {
        return Result.success(service.listMine(user.id(), query));
    }

    @GetMapping("/reviews")
    public Result<PageResult<LeaveVO>> listReviews(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute LeaveQueryReqDTO query
    ) {
        return Result.success(service.listForReview(user.id(), query));
    }

    @PutMapping("/{id}/review")
    public Result<Void> reviewLeave(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id,
        @Valid @RequestBody LeaveReviewReqDTO request
    ) {
        service.reviewLeave(user.id(), id, request);
        return Result.success(null);
    }
}
