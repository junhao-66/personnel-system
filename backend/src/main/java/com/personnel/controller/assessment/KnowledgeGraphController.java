package com.personnel.controller.assessment;

import com.personnel.common.result.Result;
import com.personnel.service.KnowledgeGraphService;
import com.personnel.vo.KnowledgeGraphVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge-graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService service;

    public KnowledgeGraphController(KnowledgeGraphService service) {
        this.service = service;
    }

    @GetMapping
    public Result<KnowledgeGraphVO> getCurrent() {
        return Result.success(service.getCurrent());
    }
}
