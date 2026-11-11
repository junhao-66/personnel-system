package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.DepartmentManagerReqDTO;
import com.personnel.dto.req.DepartmentSelfApproverReqDTO;
import com.personnel.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/departments")
public class LeaveManagerController {

    private final LeaveService service;

    public LeaveManagerController(LeaveService service) {
        this.service = service;
    }

    @PutMapping("/{id}/self-approver")
    public Result<Void> configureSelfApprover(
        @PathVariable Long id,
        @Valid @RequestBody DepartmentSelfApproverReqDTO request
    ) {
        service.configureSelfApprover(id, request.managerUserId());
        return Result.success();
    }

    @PutMapping("/{id}/manager")
    public Result<Void> configureManager(
        @PathVariable Long id,
        @Valid @RequestBody DepartmentManagerReqDTO request
    ) {
        service.configureManager(id, request.managerUserId());
        return Result.success();
    }
}
