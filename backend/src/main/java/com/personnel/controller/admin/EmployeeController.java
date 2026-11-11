package com.personnel.controller.admin;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.EmployeeQueryReqDTO;
import com.personnel.dto.req.EmployeeUpdateReqDTO;
import com.personnel.service.EmployeeService;
import com.personnel.vo.EmployeeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public Result<PageResult<EmployeeVO>> listEmployees(
        @Valid @ModelAttribute EmployeeQueryReqDTO query
    ) {
        return Result.success(employeeService.listEmployees(query));
    }

    @PutMapping("/{id}")
    public Result<Void> updateEmployee(
        @PathVariable Long id,
        @Valid @RequestBody EmployeeUpdateReqDTO request
    ) {
        employeeService.updateEmployee(id, request);
        return Result.success(null);
    }
}
