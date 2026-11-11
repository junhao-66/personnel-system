package com.personnel.interceptor;

import com.personnel.common.exception.BusinessException;
import com.personnel.common.util.AuditActorContext;
import com.personnel.common.util.JwtUtil;
import com.personnel.service.UserService;
import com.personnel.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthInterceptor(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) throws Exception {
        AuditActorContext.clear();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(401, "未登录");
            return false;
        }
        UserVO user;
        try {
            Claims claims = jwtUtil.parse(authorization.substring(7));
            user = userService.getById(Long.valueOf(claims.getSubject()));
            Number version = claims.get("authVersion", Number.class);
            if (
                !"APPROVED".equals(user.auditStatus()) ||
                !"ENABLED".equals(user.userStatus()) ||
                !user.roleCode().equals(claims.get("role", String.class)) ||
                user.authVersion() != (version == null ? 0L : version.longValue())
            ) {
                response.sendError(401, "账号状态已变化，请重新登录");
                return false;
            }
        } catch (JwtException | IllegalArgumentException | BusinessException exception) {
            response.sendError(401, "登录已失效");
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean adminOnly =
            path.startsWith("/api/admin/") ||
            path.equals("/api/user/pending") ||
            path.matches("/api/user/[^/]+/audit");
        if (adminOnly && !"ADMIN".equals(user.roleCode())) {
            response.sendError(403, "无权限");
            return false;
        }
        if (
            (path.equals("/api/leave/reviews") || path.matches("/api/leave/[^/]+/review")) &&
            !"DEPARTMENT_MANAGER".equals(user.roleCode())
        ) {
            response.sendError(403, "无权限");
            return false;
        }
        request.setAttribute("currentUser", user);
        AuditActorContext.set(user.id(), user.roleCode());
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception exception
    ) {
        AuditActorContext.clear();
    }
}
