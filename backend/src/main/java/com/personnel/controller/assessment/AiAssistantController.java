package com.personnel.controller.assessment;

import com.personnel.common.result.Result;
import com.personnel.service.AiAssistantService;
import com.personnel.vo.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AiAssistantService service;

    public AiAssistantController(AiAssistantService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Result<AiStatusVO> status(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.status(user.id()));
    }

    @PostMapping("/reports/{resultId}/expand")
    public Result<AiAnswerVO> expand(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long resultId
    ) {
        return Result.success(service.expandReport(user.id(), resultId));
    }
}
