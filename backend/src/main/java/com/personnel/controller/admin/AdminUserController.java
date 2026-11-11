package com.personnel.controller.admin;

import com.personnel.common.result.Result;
import com.personnel.dto.req.AdminUserCreateReqDTO;
import com.personnel.dto.req.UserAccessReqDTO;
import com.personnel.service.UserService;
import com.personnel.vo.UserVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/{id}/access")
    public Result<Void> updateAccess(
        @RequestAttribute("currentUser") UserVO actor,
        @PathVariable Long id,
        @Valid @RequestBody UserAccessReqDTO request
    ) {
        userService.updateAccess(actor.id(), id, request);
        return Result.success();
    }

    @GetMapping
    public Result<List<UserVO>> listUsers() {
        return Result.success(userService.listUsers());
    }

    @PostMapping
    public Result<UserVO> createUser(@Valid @RequestBody AdminUserCreateReqDTO request) {
        return Result.success(userService.createUser(request));
    }
}
