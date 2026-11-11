package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.*;
import com.personnel.dto.req.*;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.*;
import com.personnel.service.*;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Idempotent column migration runs before mapper initialization; all synthetic business rows roll back. */
@SpringBootTest(properties = {"spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:account-schema.sql,classpath:audit-schema.sql",
        "spring.sql.init.data-locations=optional:classpath:account-no-seed.sql"})
@AutoConfigureMockMvc
@Transactional
class UserAccountDatabaseIT {
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @SpyBean OperationAuditMapper audits;
    String unique() { return "ac"+UUID.randomUUID().toString().replace("-", "").substring(0,16); }
    UserVO account(String role) { return users.createUser(new AdminUserCreateReqDTO(unique(),"TestOnly123","账号验收",null,role,null,null)); }
    UserVO pending() {
        String name=unique(); users.register(new UserRegisterReqDTO(name,"TestOnly123","待审核验收",null));
        return users.listPendingUsers().stream().filter(u -> name.equals(u.userName())).findFirst().orElseThrow();
    }
    String token(UserVO user) { return "Bearer "+jwt.createToken(user.id(),user.roleCode(),user.authVersion()); }
    String credentials(UserVO user) throws Exception { return json.writeValueAsString(new UserLoginReqDTO(user.userName(),"TestOnly123")); }
    @AfterEach void cleanup() { reset(audits); AuditActorContext.clear(); }

    @Test void pendingStatusQueryIsPrivateAndDoesNotGrantBusinessAccess() throws Exception {
        var employee=pending();
        mvc.perform(post("/api/user/review-status").contentType("application/json").content(credentials(employee)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.auditStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist()).andExpect(jsonPath("$.data.phone").doesNotExist());
        mvc.perform(post("/api/user/review-status").contentType("application/json").content(json.writeValueAsBytes(new UserLoginReqDTO(employee.userName(),"WrongPassword"))))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误")).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/api/user/login").contentType("application/json").content(credentials(employee))).andExpect(jsonPath("$.code").value(400));
        for(String path:new String[]{"/api/user/me","/api/assessment/history","/api/attendance/today","/api/leave/mine"})
            mvc.perform(get(path).header("Authorization",token(employee))).andExpect(status().isUnauthorized());
    }
    @Test void rejectionRequiresReasonAndApplicantCanQuerySavedReason() throws Exception {
        var admin=account("ADMIN"); var employee=pending();
        mvc.perform(put("/api/user/"+employee.id()+"/audit").header("Authorization",token(admin)).contentType("application/json").content("{\"auditStatus\":\"REJECTED\",\"auditReason\":\"  \"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/api/user/"+employee.id()+"/audit").header("Authorization",token(admin)).contentType("application/json").content("{\"auditStatus\":\"REJECTED\",\"auditReason\":\" 请核实入职信息 \"}"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/user/review-status").contentType("application/json").content(credentials(employee)))
                .andExpect(jsonPath("$.data.auditReason").value("请核实入职信息")).andExpect(jsonPath("$.data.reviewedAt").exists())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT changed_fields FROM operation_audit WHERE target_id=? AND operation='USER_AUDIT'",String.class,employee.id()))
                .isEqualTo("auditStatus,auditReason");
        assertThat(jdbc.queryForObject("SELECT COUNT(user_id) FROM employee_profile WHERE user_id=?",Long.class,employee.id())).isZero();
        mvc.perform(post("/api/user/login").contentType("application/json").content(credentials(employee))).andExpect(jsonPath("$.code").value(400));
    }
    @Test void approvalCreatesProfileAndCannotBeReversedThroughAuditEndpoint() throws Exception {
        var employee=pending(); users.audit(employee.id(),new UserAuditReqDTO("APPROVED"));
        var login=users.login(new UserLoginReqDTO(employee.userName(),"TestOnly123"));
        mvc.perform(get("/api/user/me").header("Authorization","Bearer "+login.accessToken()))
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED")).andExpect(jsonPath("$.data.authVersion").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(user_id) FROM employee_profile WHERE user_id=?",Long.class,employee.id())).isEqualTo(1);
        assertThatThrownBy(() -> users.audit(employee.id(),new UserAuditReqDTO("REJECTED","不允许逆向修改"))).hasMessageContaining("已处理");
    }
    @Test void disableReenableAndRoleRestorationNeverReviveOldToken() throws Exception {
        var admin=account("ADMIN"); var employee=account("EMPLOYEE"); String oldToken=token(employee);
        mvc.perform(put("/api/admin/users/"+employee.id()+"/access").header("Authorization",token(admin)).contentType("application/json").content("{\"roleCode\":\"EMPLOYEE\",\"userStatus\":\"DISABLED\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/user/me").header("Authorization",oldToken)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> users.login(new UserLoginReqDTO(employee.userName(),"TestOnly123"))).hasMessageContaining("禁用");
        users.updateAccess(admin.id(),employee.id(),new UserAccessReqDTO("EMPLOYEE","ENABLED"));
        mvc.perform(get("/api/user/me").header("Authorization",oldToken)).andExpect(status().isUnauthorized());
        var fresh=users.login(new UserLoginReqDTO(employee.userName(),"TestOnly123"));
        mvc.perform(get("/api/user/me").header("Authorization","Bearer "+fresh.accessToken())).andExpect(jsonPath("$.code").value(200));
        users.updateAccess(admin.id(),employee.id(),new UserAccessReqDTO("ADMIN","ENABLED"));
        users.updateAccess(admin.id(),employee.id(),new UserAccessReqDTO("EMPLOYEE","ENABLED"));
        mvc.perform(get("/api/user/me").header("Authorization","Bearer "+fresh.accessToken())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(user_id) FROM employee_profile WHERE user_id=?",Long.class,employee.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM operation_audit WHERE target_id=? AND operation='USER_ACCESS'",Long.class,employee.id())).isEqualTo(4);
    }
    @Test void permissionsSelfProtectionAndDepartmentHandoverAreEnforced() throws Exception {
        var admin=account("ADMIN"); var employee=account("EMPLOYEE"); var manager=account("DEPARTMENT_MANAGER");
        mvc.perform(put("/api/admin/users/"+admin.id()+"/access").header("Authorization",token(employee)).contentType("application/json").content("{\"roleCode\":\"ADMIN\",\"userStatus\":\"DISABLED\"}"))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> users.updateAccess(admin.id(),admin.id(),new UserAccessReqDTO("EMPLOYEE","DISABLED"))).hasMessageContaining("自己");
        organizations.addDepartment(new DepartmentSaveReqDTO(unique(),unique(),manager.id(),0));
        assertThatThrownBy(() -> users.updateAccess(admin.id(),manager.id(),new UserAccessReqDTO("EMPLOYEE","ENABLED"))).hasMessageContaining("先移交");
        mvc.perform(put("/api/admin/users/"+employee.id()+"/access").header("Authorization",token(admin)).contentType("application/json").content("{\"roleCode\":\"SUPERUSER\",\"userStatus\":\"ENABLED\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void accessAuditFailureRollsBackRoleStatusVersionAndFixtures() {
        String marker=unique(); var transaction=new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.execute(status -> {
            var admin=account("ADMIN");
            var employee=users.createUser(new AdminUserCreateReqDTO(marker,"TestOnly123","事务验收",null,"EMPLOYEE",null,null));
            doThrow(new DataAccessResourceFailureException("simulated audit insert failure")).when(audits).insert(any(OperationAuditDO.class));
            users.updateAccess(admin.id(),employee.id(),new UserAccessReqDTO("ADMIN","DISABLED")); return null;
        })).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM sys_user WHERE user_name=?",Long.class,marker)).isZero();
    }
}
