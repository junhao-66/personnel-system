package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.GraphImportReqDTO;
import com.personnel.service.KnowledgeGraphService;
import com.personnel.vo.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/knowledge-graph")
public class KnowledgeGraphAdminController {

    private final KnowledgeGraphService service;

    public KnowledgeGraphAdminController(KnowledgeGraphService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<KnowledgeGraphVO>> list(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(service.listVersions(user.id()));
    }

    @PostMapping
    public Result<KnowledgeGraphVO> add(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody GraphImportReqDTO request
    ) {
        return Result.success(service.importVersion(user.id(), request));
    }

    @PutMapping("/{id}/publish")
    public Result<Void> publish(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        service.publishVersion(user.id(), id);
        return Result.success();
    }

    @PutMapping("/{id}/archive")
    public Result<Void> archive(
        @RequestAttribute("currentUser") UserVO user,
        @PathVariable Long id
    ) {
        service.archiveVersion(user.id(), id);
        return Result.success();
    }
}
