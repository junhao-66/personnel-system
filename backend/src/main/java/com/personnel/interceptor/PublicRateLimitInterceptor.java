package com.personnel.interceptor;

import com.personnel.common.util.RateLimitGuard;
import com.personnel.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PublicRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitGuard guard;
    private final RateLimitProperties properties;

    public PublicRateLimitInterceptor(RateLimitGuard guard, RateLimitProperties properties) {
        this.guard = guard;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {
        if (!"POST".equals(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Never trust client-supplied X-Forwarded-For / Forwarded headers as identities.
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (path.equals("/api/user/login") || path.equals("/api/user/review-status")) {
            guard.check(
                "CREDENTIAL_IP",
                address,
                properties.getCredentialIpLimit(),
                properties.getWindowSeconds()
            );
        } else if (path.equals("/api/user/register")) {
            guard.check(
                "REGISTRATION_IP",
                address,
                properties.getRegistrationIpLimit(),
                properties.getRegistrationWindowSeconds()
            );
        }
        return true;
    }
}
