package com.personnel.interceptor;

import com.personnel.common.util.RateLimitGuard;
import com.personnel.config.RateLimitProperties;
import com.personnel.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SensitiveRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitGuard guard;
    private final RateLimitProperties properties;

    public SensitiveRateLimitInterceptor(RateLimitGuard guard, RateLimitProperties properties) {
        this.guard = guard;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {
        if (!(request.getAttribute("currentUser") instanceof UserVO user)) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        String group = "PRIVATE_READ";
        int limit = properties.getReadLimit();
        if ("GET".equals(method) && path.matches("/api/assessment/results/[^/]+/pdf")) {
            group = "PDF_EXPORT";
            limit = properties.getPdfLimit();
        }
        if (!"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method)) {
            group = "PRIVATE_WRITE";
            limit = properties.getWriteLimit();
            if (path.equals("/api/user/me/password")) {
                group = "PASSWORD_CHANGE";
                limit = properties.getPasswordLimit();
            } else if (path.startsWith("/api/ai/") || path.startsWith("/api/admin/ai/")) {
                group = "AI_ENHANCEMENT";
                limit = properties.getAiLimit();
            } else if (path.matches("/api/assessment/sessions/[^/]+/answers")) {
                group = "ASSESSMENT_AUTOSAVE";
                limit = properties.getAutoSaveLimit();
            } else if (
                path.startsWith("/api/admin/assessment/") ||
                path.startsWith("/api/admin/knowledge-graph") ||
                path.startsWith("/api/admin/growth-templates") ||
                path.startsWith("/api/admin/job-preferences") ||
                path.startsWith("/api/admin/static-cache")
            ) {
                group = "CONTENT_WRITE";
                limit = properties.getContentLimit();
            } else if (path.startsWith("/api/attendance/")) {
                group = "ATTENDANCE_WRITE";
                limit = properties.getAttendanceLimit();
            } else if (path.equals("/api/leave") || path.startsWith("/api/leave/")) {
                group = "LEAVE_WRITE";
                limit = properties.getLeaveLimit();
            }
        }
        // Group by authenticated user, not raw token, target ID, URL query or client-provided userId.
        guard.check(group, user.id().toString(), limit, properties.getWindowSeconds());
        return true;
    }
}
