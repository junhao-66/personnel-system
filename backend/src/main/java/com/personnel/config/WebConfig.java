package com.personnel.config;

import com.personnel.interceptor.AuthInterceptor;
import com.personnel.interceptor.PublicRateLimitInterceptor;
import com.personnel.interceptor.SensitiveRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final PublicRateLimitInterceptor publicLimits;
    private final SensitiveRateLimitInterceptor sensitiveLimits;

    public WebConfig(
        AuthInterceptor authInterceptor,
        PublicRateLimitInterceptor publicLimits,
        SensitiveRateLimitInterceptor sensitiveLimits
    ) {
        this.authInterceptor = authInterceptor;
        this.publicLimits = publicLimits;
        this.sensitiveLimits = sensitiveLimits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(publicLimits)
            .addPathPatterns("/api/user/login", "/api/user/register", "/api/user/review-status")
            .order(0);
        registry
            .addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .order(1)
            .excludePathPatterns(
                "/api/user/login",
                "/api/user/register",
                "/api/user/review-status",
                "/api/system/health"
            );
        registry.addInterceptor(sensitiveLimits).addPathPatterns("/api/**").order(2);
    }
}
