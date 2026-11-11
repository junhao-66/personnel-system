package com.personnel.controller.user;

import com.personnel.common.result.Result;
import com.personnel.dto.req.AccountContactReqDTO;
import com.personnel.dto.req.AccountPasswordReqDTO;
import com.personnel.service.UserService;
import com.personnel.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/me")
public class SessionController {

    private final UserService users;

    public SessionController(UserService users) {
        this.users = users;
    }

    @PutMapping("/contact")
    public Result<UserVO> updateContact(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody AccountContactReqDTO request
    ) {
        return Result.success(users.updateOwnContact(user.id(), request));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(
        @RequestAttribute("currentUser") UserVO user,
        @Valid @RequestBody AccountPasswordReqDTO request
    ) {
        users.changeOwnPassword(user.id(), request);
        return Result.success();
    }

    @GetMapping
    public Result<UserVO> currentUser(@RequestAttribute("currentUser") UserVO user) {
        return Result.success(user);
    }
}
