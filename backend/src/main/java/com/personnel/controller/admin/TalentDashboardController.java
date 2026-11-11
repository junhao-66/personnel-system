package com.personnel.controller.admin;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.TalentQueryReqDTO;
import com.personnel.service.TalentDashboardService;
import com.personnel.vo.TalentDashboardVO;
import com.personnel.vo.TalentEmployeeVO;
import com.personnel.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/talent")
public class TalentDashboardController {

    private final TalentDashboardService service;

    public TalentDashboardController(TalentDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Result<TalentDashboardVO> getDashboard(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute TalentQueryReqDTO query
    ) {
        return Result.success(service.getDashboard(user.id(), query));
    }

    @GetMapping("/employees")
    public Result<PageResult<TalentEmployeeVO>> listEmployees(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute TalentQueryReqDTO query
    ) {
        return Result.success(service.listEmployees(user.id(), query));
    }

    @GetMapping("/employees/{id}")
    public Result<TalentEmployeeVO> getEmployee(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        return Result.success(service.getEmployee(user.id(), id));
    }
}
