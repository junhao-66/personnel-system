package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.DepartmentSaveReqDTO;
import com.personnel.dto.req.PositionSaveReqDTO;
import com.personnel.service.OrganizationService;
import com.personnel.vo.DepartmentVO;
import com.personnel.vo.PositionVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/departments")
    public Result<List<DepartmentVO>> listDepartments() {
        return Result.success(organizationService.listDepartments());
    }

    @PostMapping("/departments")
    public Result<DepartmentVO> addDepartment(@Valid @RequestBody DepartmentSaveReqDTO request) {
        return Result.success(organizationService.addDepartment(request));
    }

    @PutMapping("/departments/{id}/enabled")
    public Result<Void> setDepartmentEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        organizationService.setDepartmentEnabled(id, enabled);
        return Result.success();
    }

    @PutMapping("/departments/{id}")
    public Result<DepartmentVO> updateDepartment(
        @PathVariable Long id,
        @Valid @RequestBody DepartmentSaveReqDTO request
    ) {
        return Result.success(organizationService.updateDepartment(id, request));
    }

    @GetMapping("/positions")
    public Result<List<PositionVO>> listPositions(
        @RequestParam(required = false) Long departmentId
    ) {
        return Result.success(organizationService.listPositions(departmentId));
    }

    @PostMapping("/positions")
    public Result<PositionVO> addPosition(@Valid @RequestBody PositionSaveReqDTO request) {
        return Result.success(organizationService.addPosition(request));
    }

    @PutMapping("/positions/{id}/enabled")
    public Result<Void> setPositionEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        organizationService.setPositionEnabled(id, enabled);
        return Result.success();
    }

    @PutMapping("/positions/{id}")
    public Result<PositionVO> updatePosition(
        @PathVariable Long id,
        @Valid @RequestBody PositionSaveReqDTO request
    ) {
        return Result.success(organizationService.updatePosition(id, request));
    }
}
