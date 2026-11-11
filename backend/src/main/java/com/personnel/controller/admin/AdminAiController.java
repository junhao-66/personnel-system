package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.AiQuestionReqDTO;
import com.personnel.service.AiAssistantService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {

    private final AiAssistantService service;

    public AdminAiController(AiAssistantService service) {
        this.service = service;
    }

    @PostMapping("/employees/{userId}/ask")
    public Result<AiAnswerVO> employee(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long userId,
        @Valid @RequestBody AiQuestionReqDTO request
    ) {
        return Result.success(service.askEmployee(user.id(), userId, request.question()));
    }

    @PostMapping("/departments/{departmentId}/ask")
    public Result<AiAnswerVO> department(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable @Min(0) Long departmentId,
        @Valid @RequestBody AiQuestionReqDTO request
    ) {
        return Result.success(service.askDepartment(user.id(), departmentId, request.question()));
    }
}
