package com.personnel.controller.system;

import com.personnel.common.result.Result;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of("status", "UP", "service", "personnel-system"));
    }
}
