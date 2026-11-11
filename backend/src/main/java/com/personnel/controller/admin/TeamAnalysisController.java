package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.service.TeamAnalysisService;
import com.personnel.vo.*;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/team-analysis")
public class TeamAnalysisController {

    private final TeamAnalysisService service;

    public TeamAnalysisController(TeamAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public Result<TeamAnalysisVO> analyze(
        @RequestAttribute("currentUser") UserVO user,
        @RequestParam @Min(0) Long departmentId
    ) {
        return Result.success(service.analyzeDepartment(user.id(), departmentId));
    }
}
