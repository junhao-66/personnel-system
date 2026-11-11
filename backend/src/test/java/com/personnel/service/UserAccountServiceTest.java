package com.personnel.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personnel.common.constant.AuditOperation;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.entity.UserDO;
import com.personnel.mapper.*;
import com.personnel.service.impl.UserServiceImpl;
import com.personnel.vo.DepartmentVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAccountServiceTest {
    UserMapper mapper = mock(UserMapper.class);
    EmployeeProfileMapper profiles = mock(EmployeeProfileMapper.class);
    OrganizationService organizations = mock(OrganizationService.class);
    OperationAuditService audit = mock(OperationAuditService.class);
    JwtUtil jwt = new JwtUtil("test-only-long-signing-key-for-account-tests-at-least-48-bytes");
    UserService service;
    static final String HASH = new BCryptPasswordEncoder().encode("TestOnly123");
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), UserDO.class);
        service = new UserServiceImpl(mapper, profiles, organizations, jwt, audit,
                Clock.fixed(Instant.parse("2026-10-29T03:00:00Z"), ZoneId.of("Asia/Shanghai")), mock(com.personnel.common.util.RateLimitGuard.class));
    }
    UserDO user(long id,String role,String status) {
        var value=new UserDO(); value.setId(id); value.setUserName("test-account"); value.setPasswordHash(HASH);
        value.setRoleCode(role); value.setAuditStatus(status); value.setUserStatus("ENABLED"); value.setAuthVersion(0L); return value;
    }
    void access(UserDO target) {
        when(mapper.selectEnabledAdminIdsForUpdate()).thenReturn(List.of(9L));
        when(mapper.selectById(9L)).thenReturn(user(9,"ADMIN","APPROVED"));
        when(mapper.lockUser(target.getId())).thenReturn(target.getId());
        if(target.getId()!=9L) when(mapper.selectById(target.getId())).thenReturn(target);
    }
    @Test void pendingAndRejectedCredentialsCanReadOwnReviewButCannotLogin() {
        var value=user(8,"EMPLOYEE","PENDING"); when(mapper.selectOne(any())).thenReturn(value);
        var credentials=new UserLoginReqDTO("test-account","TestOnly123");
        assertThat(service.getReviewStatus(credentials).auditStatus()).isEqualTo("PENDING");
        assertThatThrownBy(() -> service.login(credentials)).hasMessageContaining("尚未通过");
        value.setAuditStatus("REJECTED"); value.setAuditReason("请核实入职信息");
        assertThat(service.getReviewStatus(credentials).auditReason()).isEqualTo("请核实入职信息");
        assertThatThrownBy(() -> service.login(credentials)).hasMessageContaining("尚未通过");
    }
    @Test void unknownAndWrongPasswordsHaveSameError() {
        var credentials=new UserLoginReqDTO("test-account","wrong");
        assertThatThrownBy(() -> service.getReviewStatus(credentials)).hasMessage("用户名或密码错误");
        when(mapper.selectOne(any())).thenReturn(user(8,"EMPLOYEE","PENDING"));
        assertThatThrownBy(() -> service.getReviewStatus(credentials)).hasMessage("用户名或密码错误");
    }
    @Test void rejectRequiresNonblankReasonBeforeAnyWrite() {
        assertThatThrownBy(() -> service.audit(8L,new UserAuditReqDTO("REJECTED","  "))).hasMessageContaining("必须填写");
        verifyNoInteractions(mapper,profiles,audit);
    }
    @Test void completedApprovalCannotBeUsedToDisableAdministrator() {
        when(mapper.lockUser(9L)).thenReturn(9L); when(mapper.selectById(9L)).thenReturn(user(9,"ADMIN","APPROVED"));
        assertThatThrownBy(() -> service.audit(9L,new UserAuditReqDTO("REJECTED","不允许"))).hasMessageContaining("已处理");
        verifyNoInteractions(audit,profiles);
    }
    @Test void administratorCannotDisableOrDemoteSelf() {
        access(user(9,"ADMIN","APPROVED"));
        assertThatThrownBy(() -> service.updateAccess(9L,9L,new UserAccessReqDTO("EMPLOYEE","DISABLED"))).hasMessageContaining("自己");
        verifyNoInteractions(audit);
    }
    @Test void managerMustTransferAssignedDepartmentsBeforeDemotion() {
        access(user(8,"DEPARTMENT_MANAGER","APPROVED"));
        when(organizations.listDepartments()).thenReturn(List.of(new DepartmentVO(1L,"研发","DEV",8L,0,1)));
        assertThatThrownBy(() -> service.updateAccess(9L,8L,new UserAccessReqDTO("EMPLOYEE","ENABLED"))).hasMessageContaining("先移交");
        verifyNoInteractions(audit);
    }
    @Test void unchangedAccessCreatesNoAuditAndDoesNotInvalidateSession() {
        access(user(8,"EMPLOYEE","APPROVED"));
        service.updateAccess(9L,8L,new UserAccessReqDTO("EMPLOYEE","ENABLED"));
        verifyNoInteractions(audit,profiles);
        verify(mapper,never()).update(isNull(),any());
    }
    @Test void accessChangesAreAuditedWithoutTouchingEmployeeProfile() {
        access(user(8,"EMPLOYEE","APPROVED"));
        service.updateAccess(9L,8L,new UserAccessReqDTO("ADMIN","DISABLED"));
        verify(audit).append(AuditOperation.USER_ACCESS,8L,List.of("roleCode","userStatus"));
        verifyNoInteractions(profiles);
    }
    @Test void approvedLoginTokenUsesCurrentVersion() {
        var value=user(8,"EMPLOYEE","APPROVED"); value.setAuthVersion(4L); when(mapper.selectOne(any())).thenReturn(value);
        var token=service.login(new UserLoginReqDTO("test-account","TestOnly123")).accessToken();
        assertThat(jwt.parse(token).get("authVersion",Number.class).longValue()).isEqualTo(4);
    }
}
