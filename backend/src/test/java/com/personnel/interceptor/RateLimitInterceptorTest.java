package com.personnel.interceptor;

import com.personnel.common.util.RateLimitGuard;
import com.personnel.common.exception.GlobalExceptionHandler;
import com.personnel.config.RateLimitProperties;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateLimitInterceptorTest {
    @RestController static class Endpoint {
        @PostMapping({"/api/user/login", "/api/user/review-status", "/api/user/register"})
        public String call() { return "ok"; }
    }
    @Test void publicSharedIpLimitReturns429BeforeControllerAndIgnoresForwardedHeader() throws Exception {
        var properties = new RateLimitProperties(); properties.setCredentialIpLimit(1);
        var endpoint = spy(new Endpoint());
        var mvc = MockMvcBuilders.standaloneSetup(endpoint).setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new PublicRateLimitInterceptor(new RateLimitGuard(properties), properties)).build();
        mvc.perform(post("/api/user/login").header("X-Forwarded-For", "1.1.1.1")).andExpect(status().isOk());
        mvc.perform(post("/api/user/review-status").header("X-Forwarded-For", "2.2.2.2"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value(429));
        verify(endpoint, times(1)).call();
        mvc.perform(post("/api/user/register")).andExpect(status().isOk());
    }
    MockHttpServletRequest request(String path, Long userId) {
        var request = new MockHttpServletRequest("PUT", path);
        request.setAttribute("currentUser", new UserVO(userId,"test","Test",null,"EMPLOYEE","APPROVED","ENABLED",null,null));
        return request;
    }
    @Test void sensitiveGroupsUseAuthenticatedUserAndKeepAutosaveSeparate() {
        var properties = new RateLimitProperties(); properties.setWriteLimit(1); properties.setAutoSaveLimit(1);
        var interceptor = new SensitiveRateLimitInterceptor(new RateLimitGuard(properties), properties);
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/profile", 1L), response, null)).isTrue();
        var spoofed = request("/api/profile/other", 1L); spoofed.addParameter("userId", "2");
        assertThatThrownBy(() -> interceptor.preHandle(spoofed, response, null))
                .isInstanceOf(com.personnel.common.exception.RateLimitExceededException.class);
        assertThat(interceptor.preHandle(request("/api/profile", 2L), response, null)).isTrue();
        assertThat(interceptor.preHandle(request("/api/assessment/sessions/1/answers", 1L), response, null)).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request("/api/assessment/sessions/2/answers", 1L), response, null))
                .isInstanceOf(com.personnel.common.exception.RateLimitExceededException.class);
    }
    @Test void missingAuthenticationDoesNotCreateAnonymousPrivateBuckets() {
        var guard = mock(RateLimitGuard.class);
        var interceptor = new SensitiveRateLimitInterceptor(guard, new RateLimitProperties());
        assertThat(interceptor.preHandle(new MockHttpServletRequest("PUT", "/api/profile"), new MockHttpServletResponse(), null)).isTrue();
        verifyNoInteractions(guard);
    }
}
