package com.personnel.interceptor;

import com.personnel.common.util.JwtUtil;
import com.personnel.service.UserService;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.personnel.common.util.AuditActorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {
    @Mock UserService users;
    JwtUtil jwt = new JwtUtil("test-only-long-signing-key-for-auth-tests-at-least-48-bytes");
    AuthInterceptor interceptor;
    @BeforeEach void setUp() { interceptor = new AuthInterceptor(jwt, users); }
    @AfterEach void clearActor() { AuditActorContext.clear(); }
    @Test void authenticatedActorIsClearedAfterRequestIncludingFailure() throws Exception {
        when(users.getById(9L)).thenReturn(user("ADMIN", "APPROVED", "ENABLED"));
        var request = request("/api/admin/employees", "ADMIN");
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(AuditActorContext.get().userId()).isEqualTo(9L);
        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("test"));
        assertThat(AuditActorContext.get()).isNull();
    }
    UserVO user(String role, String audit, String status) { return new UserVO(9L, "testuser", "测试员工", null, role, audit, status, null, null); }
    MockHttpServletRequest request(String path, String tokenRole) {
        var request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + jwt.createToken(9L, tokenRole)); return request;
    }
    @Test void missingTokenIsUnauthorized() throws Exception {
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401); verifyNoInteractions(users);
    }
    @Test void employeeCanUseOwnEndpointsAndGetsServerIdentity() throws Exception {
        when(users.getById(9L)).thenReturn(user("EMPLOYEE", "APPROVED", "ENABLED"));
        var request = request("/api/attendance/today", "EMPLOYEE");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(((UserVO) request.getAttribute("currentUser")).id()).isEqualTo(9L);
    }
    @Test void employeeCannotReadAdminData() throws Exception {
        when(users.getById(9L)).thenReturn(user("EMPLOYEE", "APPROVED", "ENABLED"));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/admin/attendance/records", "EMPLOYEE"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void pendingAccountCannotUseExistingToken() throws Exception {
        when(users.getById(9L)).thenReturn(user("EMPLOYEE", "PENDING", "ENABLED"));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/attendance/today", "EMPLOYEE"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
    @Test void disabledAccountCannotUseExistingToken() throws Exception {
        when(users.getById(9L)).thenReturn(user("ADMIN", "APPROVED", "DISABLED"));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/admin/users", "ADMIN"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
    @Test void oldRoleTokenIsRejectedAfterRoleChange() throws Exception {
        when(users.getById(9L)).thenReturn(user("EMPLOYEE", "APPROVED", "ENABLED"));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/admin/users", "ADMIN"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
    @Test void oldVersionTokenStaysInvalidAfterReenableOrRoleRestoration() throws Exception {
        when(users.getById(9L)).thenReturn(new UserVO(9L,"testuser","测试",null,"EMPLOYEE","APPROVED","ENABLED",null,null,null,null,2L));
        var response=new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/user/me","EMPLOYEE"),response,new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
    @Test void currentVersionTokenIsAccepted() throws Exception {
        when(users.getById(9L)).thenReturn(new UserVO(9L,"testuser","测试",null,"EMPLOYEE","APPROVED","ENABLED",null,null,null,null,2L));
        var request=new MockHttpServletRequest("GET","/api/user/me");
        request.addHeader("Authorization","Bearer "+jwt.createToken(9L,"EMPLOYEE",2L));
        assertThat(interceptor.preHandle(request,new MockHttpServletResponse(),new Object())).isTrue();
    }
    @Test void tokenSignedByDifferentProcessKeyIsRejected() throws Exception {
        JwtUtil other = new JwtUtil("");
        var request = new MockHttpServletRequest("GET", "/api/attendance/today");
        request.addHeader("Authorization", "Bearer " + other.createToken(9L, "ADMIN"));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401); verifyNoInteractions(users);
    }
}
