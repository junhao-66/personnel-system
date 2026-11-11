package com.personnel;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.AuditActorContext;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.*;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Adds the audit table idempotently; synthetic business rows roll back. */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@Sql(scripts = "classpath:audit-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Transactional
class OperationAuditDatabaseIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired EmployeeService employees;
    @Autowired OrganizationService organizations;
    @Autowired OperationAuditQueryService auditQuery;
    @SpyBean OperationAuditMapper audits;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired PlatformTransactionManager transactions;
    String unique() { return "au" + UUID.randomUUID().toString().replace("-", "").substring(0,16); }
    UserVO account(String role) { return users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "审计验收", null, role, null, null)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode()); }
    @AfterEach void cleanup() { AuditActorContext.clear(); reset(audits); }

    @Test void employeeMutationCapturesServerActorAndOnlyChangedFieldNames() throws Exception {
        var admin = account("ADMIN"); var employee = account("EMPLOYEE");
        var request = new EmployeeUpdateReqDTO("姓名原文不可入日志", "13800138000", null, null, LocalDate.of(2020,1,1), "RESIGNED");
        mvc.perform(put("/api/admin/employees/" + employee.id()).header("Authorization", bearer(admin))
                .param("actorUserId", "999999").contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(jsonPath("$.code").value(200));
        assertThat(AuditActorContext.get()).isNull();
        var entry = audits.selectOne(Wrappers.<OperationAuditDO>lambdaQuery().eq(OperationAuditDO::getTargetId, employee.id()).eq(OperationAuditDO::getOperation,"EMPLOYEE_UPDATE"));
        assertThat(entry.getActorUserId()).isEqualTo(admin.id());
        assertThat(entry.getChangedFields()).isEqualTo("realName,phone,hireDate,employmentStatus");
        assertThat(json.writeValueAsString(entry)).doesNotContain("13800138000", "姓名原文不可入日志", "TestOnly123");
        mvc.perform(get("/api/admin/operation-audits").header("Authorization",bearer(admin)).param("actorUserId",admin.id().toString()).param("targetId",employee.id().toString()).param("operation","EMPLOYEE_UPDATE"))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].changedFields[0]").value("realName"));
        employees.updateEmployee(employee.id(), request);
        assertThat(audits.selectCount(Wrappers.<OperationAuditDO>lambdaQuery().eq(OperationAuditDO::getTargetId,employee.id()).eq(OperationAuditDO::getOperation,"EMPLOYEE_UPDATE"))).isEqualTo(1);
    }
    @Test void accountAuditAndOrganizationOperationsArePersisted() throws Exception {
        var admin = account("ADMIN");
        AuditActorContext.set(admin.id(), "ADMIN");
        account("EMPLOYEE");
        var department = organizations.addDepartment(new DepartmentSaveReqDTO(unique(),unique(),null,0));
        var position = organizations.addPosition(new PositionSaveReqDTO(unique(),unique(),department.id(),null));
        organizations.setDepartmentEnabled(department.id(),false);
        organizations.setPositionEnabled(position.id(),false);
        organizations.updateDepartmentManager(department.id(),admin.id());
        organizations.updateDepartmentManager(department.id(),null);
        assertThat(organizations.listDepartments().stream().filter(d -> d.id().equals(department.id())).findFirst().orElseThrow().managerUserId()).isNull();
        String name=unique(); users.register(new UserRegisterReqDTO(name,"TestOnly123","注册验收",null));
        var pending = users.listPendingUsers().stream().filter(u -> u.userName().equals(name)).findFirst().orElseThrow();
        users.audit(pending.id(),new UserAuditReqDTO("APPROVED"));
        var query = new OperationAuditQueryReqDTO(); query.setActorUserId(admin.id());
        var result = auditQuery.listAuditLogs(admin.id(),query);
        assertThat(result.records()).extracting(com.personnel.vo.OperationAuditVO::operation)
                .contains("USER_CREATE","DEPARTMENT_CREATE","POSITION_CREATE","DEPARTMENT_STATUS","POSITION_STATUS","DEPARTMENT_MANAGER","USER_AUDIT");
    }
    @Test void auditApiIsReadOnlyAdminOnlyAndValidatesFilters() throws Exception {
        mvc.perform(get("/api/admin/operation-audits")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"EMPLOYEE","DEPARTMENT_MANAGER"}) {
            var user=account(role);
            mvc.perform(get("/api/admin/operation-audits").header("Authorization",bearer(user))).andExpect(status().isForbidden());
            assertThatThrownBy(() -> auditQuery.listAuditLogs(user.id(),new OperationAuditQueryReqDTO())).hasMessageContaining("无权");
        }
        var admin=account("ADMIN");
        for(String[] pair:new String[][]{{"pageSize","101"},{"actorUserId","0"},{"operation","OTHER"},{"targetType","OTHER"},{"startDate","bad"}})
            mvc.perform(get("/api/admin/operation-audits").header("Authorization",bearer(admin)).param(pair[0],pair[1])).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/admin/operation-audits").header("Authorization",bearer(admin)).param("startDate","2026-10-29").param("endDate","2026-10-28")).andExpect(jsonPath("$.code").value(400));
        mvc.perform(delete("/api/admin/operation-audits/1").header("Authorization",bearer(admin))).andExpect(status().is4xxClientError());
    }
    @Test void filtersDateBoundariesPaginationAndEmptyPageAreConsistent() {
        var admin=account("ADMIN"); AuditActorContext.set(admin.id(),"ADMIN");
        var first=organizations.addDepartment(new DepartmentSaveReqDTO(unique(),unique(),null,0));
        var second=organizations.addDepartment(new DepartmentSaveReqDTO(unique(),unique(),null,0));
        var query=new OperationAuditQueryReqDTO(); query.setActorUserId(admin.id()); query.setTargetType("DEPARTMENT"); query.setOperation("DEPARTMENT_CREATE");
        query.setStartDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))); query.setEndDate(query.getStartDate()); query.setPageSize(1);
        var page=auditQuery.listAuditLogs(admin.id(),query); assertThat(page.total()).isEqualTo(2); assertThat(page.records().get(0).targetId()).isEqualTo(second.id());
        query.setPageNum(2); assertThat(auditQuery.listAuditLogs(admin.id(),query).records().get(0).targetId()).isEqualTo(first.id());
        query.setPageNum(3); assertThat(auditQuery.listAuditLogs(admin.id(),query).records()).isEmpty();
        query.setPageNum(1); query.setTargetId(Long.MAX_VALUE); assertThat(auditQuery.listAuditLogs(admin.id(),query).total()).isZero();
    }
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedAuditInsertRollsBackBusinessAndEarlierAuditRows() {
        String marker=unique(); var target=new AtomicReference<Long>();
        var transaction=new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.execute(status -> {
            var user=users.createUser(new AdminUserCreateReqDTO(marker,"TestOnly123","事务验收",null,"EMPLOYEE",null,null)); target.set(user.id());
            doThrow(new DataAccessResourceFailureException("simulated audit insert failure")).when(audits).insert(any(OperationAuditDO.class));
            employees.updateEmployee(user.id(),new EmployeeUpdateReqDTO("不应提交",null,null,null,null,"RESIGNED"));
            return null;
        })).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM sys_user WHERE user_name = ?",Long.class,marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM operation_audit WHERE target_type IN ('USER','EMPLOYEE') AND target_id = ?",Long.class,target.get())).isZero();
    }
}
