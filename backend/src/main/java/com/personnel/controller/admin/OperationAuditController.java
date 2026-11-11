package com.personnel.controller.admin;

import com.personnel.common.base.PageResult;
import com.personnel.common.result.Result;
import com.personnel.dto.req.OperationAuditQueryReqDTO;
import com.personnel.service.OperationAuditQueryService;
import com.personnel.vo.OperationAuditVO;
import com.personnel.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/operation-audits")
public class OperationAuditController {

    private final OperationAuditQueryService service;

    public OperationAuditController(OperationAuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<OperationAuditVO>> listAuditLogs(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @ModelAttribute OperationAuditQueryReqDTO query
    ) {
        return Result.success(service.listAuditLogs(user.id(), query));
    }
}
