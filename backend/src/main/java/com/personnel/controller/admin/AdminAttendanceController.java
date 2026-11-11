package com.personnel.controller.admin;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.AdminAttendanceQueryReqDTO;
import com.personnel.dto.req.AttendanceRuleUpdateReqDTO;
import com.personnel.service.AttendanceService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    public AdminAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/records")
    public Result<PageResult<AttendanceEmployeeVO>> listAll(
        @Valid @ModelAttribute AdminAttendanceQueryReqDTO query
    ) {
        return Result.success(attendanceService.listAll(query));
    }

    @PutMapping("/rule")
    public Result<Void> updateRule(@Valid @RequestBody AttendanceRuleUpdateReqDTO request) {
        attendanceService.updateRule(request);
        return Result.success(null);
    }
}
