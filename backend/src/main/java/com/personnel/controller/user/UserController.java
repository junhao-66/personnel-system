package com.personnel.controller.user;

import com.personnel.common.result.Result;
import com.personnel.dto.req.UserAuditReqDTO;
import com.personnel.dto.req.UserLoginReqDTO;
import com.personnel.dto.req.UserRegisterReqDTO;
import com.personnel.service.UserService;
import com.personnel.vo.AccountReviewVO;
import com.personnel.vo.LoginVO;
import com.personnel.vo.UserVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterReqDTO request) {
        userService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginReqDTO request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/review-status")
    public Result<AccountReviewVO> getReviewStatus(@Valid @RequestBody UserLoginReqDTO request) {
        return Result.success(userService.getReviewStatus(request));
    }

    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody UserAuditReqDTO request) {
        userService.audit(id, request);
        return Result.success();
    }

    @GetMapping("/pending")
    public Result<List<UserVO>> listPendingUsers() {
        return Result.success(userService.listPendingUsers());
    }
}
