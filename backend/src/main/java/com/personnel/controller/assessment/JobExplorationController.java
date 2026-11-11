package com.personnel.controller.assessment;

import com.personnel.common.result.Result;
import com.personnel.service.JobPreferenceService;
import com.personnel.vo.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/job-preferences")
public class JobExplorationController {

    private final JobPreferenceService service;

    public JobExplorationController(JobPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Result<JobExplorationVO> explore(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.explore(user.id(), user.id()));
    }
}
