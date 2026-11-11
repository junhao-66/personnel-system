package com.personnel.controller.attendance;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.AttendanceQueryReqDTO;
import com.personnel.service.AttendanceService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/rule")
    public Result<AttendanceRuleVO> getRule() {
        return Result.success(attendanceService.getRule());
    }

    @GetMapping("/today")
    public Result<TodayAttendanceVO> getToday(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(attendanceService.getToday(user.id()));
    }

    @PostMapping("/clock-in")
    public Result<AttendanceRecordVO> clockIn(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(attendanceService.clockIn(user.id()));
    }

    @PostMapping("/clock-out")
    public Result<AttendanceRecordVO> clockOut(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(attendanceService.clockOut(user.id()));
    }

    @GetMapping("/records")
    public Result<PageResult<AttendanceEmployeeVO>> listMine(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute AttendanceQueryReqDTO query
    ) {
        return Result.success(attendanceService.listMine(user.id(), query));
    }
}
