package com.personnel;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.AuditActorContext;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL business/audit atomicity. Synthetic fixtures roll back after every test. */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@Transactional
class ExtendedAuditDatabaseIT {
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired LeaveService leaves;
    @Autowired AssessmentScaleService scales;
    @Autowired KnowledgeGraphService graphs;
    @Autowired AttendanceService attendance;
    @Autowired OperationAuditQueryService queries;
    @SpyBean OperationAuditMapper audits;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired PlatformTransactionManager transactions;
    String unique() { return "xa" + UUID.randomUUID().toString().replace("-", "").substring(0,16); }
    UserVO account(String role, Long department) {
        return users.createUser(new AdminUserCreateReqDTO(unique(),"TestOnly123","审计测试",null,role,department,null));
    }
    GraphImportReqDTO graph(String version) throws com.fasterxml.jackson.core.JsonProcessingException {
        var original = json.readValue(json.writeValueAsString(graphs.getCurrent()), GraphImportReqDTO.class);
        return new GraphImportReqDTO(version, original.title(),original.sources(),original.nodes(),original.edges(),original.rules());
    }
    OperationAuditDO entry(String operation, Long id) {
        return audits.selectOne(Wrappers.<OperationAuditDO>lambdaQuery().eq(OperationAuditDO::getOperation,operation).eq(OperationAuditDO::getTargetId,id));
    }
    long count(String operation, Long id) {
        return audits.selectCount(Wrappers.<OperationAuditDO>lambdaQuery().eq(OperationAuditDO::getOperation,operation).eq(OperationAuditDO::getTargetId,id));
    }
    LeaveVO leave() {
        var department = organizations.addDepartment(new DepartmentSaveReqDTO(unique(), unique(),null,0));
        var manager = account("DEPARTMENT_MANAGER", department.id());
        organizations.updateDepartmentManager(department.id(), manager.id());
        var employee = account("EMPLOYEE", department.id());
        var start = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(7).atTime(9,0);
        AuditActorContext.set(employee.id(),"EMPLOYEE");
        return leaves.addLeave(employee.id(),new LeaveCreateReqDTO("事假",start,start.plusHours(1),"PRIVATE_LEAVE_REASON"));
    }
    Long manager(LeaveVO leave) {
        return organizations.listDepartments().stream().filter(d -> d.id().equals(leave.departmentId())).findFirst().orElseThrow().managerUserId();
    }
    @AfterEach void cleanup() { reset(audits); AuditActorContext.clear(); }

    @Test void contentLifecycleIsRecordedAndFailedRepeatCreatesNoExtraEntry() throws Exception {
        var admin = account("ADMIN",null); AuditActorContext.set(admin.id(),"ADMIN");
        var scale = scales.importScale(admin.id(),AssessmentFixtures.scale(unique(),"ORIGINAL-TEST-FIXTURE"));
        scales.publishScale(admin.id(),scale.id(),new ScalePublishReqDTO(true,true,true));
        assertThatThrownBy(() -> scales.publishScale(admin.id(),scale.id(),new ScalePublishReqDTO(true,true,true))).isInstanceOf(RuntimeException.class);
        scales.archiveScale(admin.id(),scale.id());
        var graph = graphs.importVersion(admin.id(),graph(unique()));
        graphs.publishVersion(admin.id(),graph.id());
        assertThatThrownBy(() -> graphs.publishVersion(admin.id(),graph.id())).isInstanceOf(RuntimeException.class);
        graphs.archiveVersion(admin.id(),graph.id());
        for (String operation : List.of("SCALE_IMPORT","SCALE_PUBLISH","SCALE_ARCHIVE","GRAPH_IMPORT","GRAPH_PUBLISH","GRAPH_ARCHIVE")) {
            Long id = operation.startsWith("SCALE") ? scale.id() : graph.id();
            assertThat(count(operation,id)).isEqualTo(1);
            assertThat(entry(operation,id).getActorUserId()).isEqualTo(admin.id());
            assertThat(json.writeValueAsString(entry(operation,id))).doesNotContain("ORIGINAL-TEST-FIXTURE","TestOnly123");
            mvc.perform(get("/api/admin/operation-audits").header("Authorization","Bearer "+jwt.createToken(admin.id(),"ADMIN"))
                    .param("operation",operation).param("targetType",operation.startsWith("SCALE")?"SCALE":"GRAPH").param("targetId",id.toString()))
                    .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(1));
        }
    }
    @Test void leaveReviewCapturesServerActorAndHidesPrivateText() throws Exception {
        var leave = leave(); var manager = manager(leave);
        assertThat(entry("LEAVE_CREATE",leave.id()).getActorUserId()).isEqualTo(leave.userId());
        mvc.perform(put("/api/leave/"+leave.id()+"/review").header("Authorization","Bearer "+jwt.createToken(manager,"DEPARTMENT_MANAGER"))
                .param("actorUserId","999999").contentType("application/json")
                .content(json.writeValueAsBytes(new LeaveReviewReqDTO("REJECTED","PRIVATE_REVIEW_COMMENT"))))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(AuditActorContext.get()).isNull();
        var entry = entry("LEAVE_REVIEW",leave.id());
        assertThat(entry.getActorUserId()).isEqualTo(manager);
        assertThat(entry.getActorRole()).isEqualTo("DEPARTMENT_MANAGER");
        assertThat(entry.getChangedFields()).isEqualTo("status,reviewComment,reviewerId,reviewedAt");
        assertThat(json.writeValueAsString(entry)).doesNotContain("PRIVATE_REVIEW_COMMENT","PRIVATE_LEAVE_REASON");
        assertThatThrownBy(() -> leaves.reviewLeave(manager,leave.id(),new LeaveReviewReqDTO("APPROVED",""))).isInstanceOf(RuntimeException.class);
        assertThat(count("LEAVE_REVIEW",leave.id())).isEqualTo(1);
    }
    @Test void ruleOnlyRecordsActualFieldsAndNoopDoesNotWriteHistory() {
        var admin = account("ADMIN",null); AuditActorContext.set(admin.id(),"ADMIN");
        var rule = attendance.getRule(); long before = count("ATTENDANCE_RULE_UPDATE",1L);
        attendance.updateRule(new AttendanceRuleUpdateReqDTO(rule.workStartTime(),rule.workEndTime()));
        assertThat(count("ATTENDANCE_RULE_UPDATE",1L)).isEqualTo(before);
        var start = rule.workStartTime().equals(LocalTime.of(0,0)) ? LocalTime.of(0,1) : rule.workStartTime().minusMinutes(1);
        attendance.updateRule(new AttendanceRuleUpdateReqDTO(start,rule.workEndTime()));
        var query = new OperationAuditQueryReqDTO(); query.setOperation("ATTENDANCE_RULE_UPDATE"); query.setActorUserId(admin.id());
        assertThat(queries.listAuditLogs(admin.id(),query).records()).singleElement().satisfies(row -> {
            assertThat(row.changedFields()).containsExactly("workStartTime"); assertThat(row.targetType()).isEqualTo("ATTENDANCE_RULE");
        });
    }
    @ParameterizedTest
    @ValueSource(strings={"LEAVE_CREATE","LEAVE_REVIEW","SCALE_IMPORT","SCALE_PUBLISH","SCALE_ARCHIVE","GRAPH_IMPORT","GRAPH_PUBLISH","GRAPH_ARCHIVE","ATTENDANCE_RULE_UPDATE"})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackEachBusinessOperation(String operation) {
        String marker = unique(); var originalRule = attendance.getRule();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            var admin = users.createUser(new AdminUserCreateReqDTO(marker,"TestOnly123","回滚测试",null,"ADMIN",null,null));
            AuditActorContext.set(admin.id(),"ADMIN");
            // Fail only the audit row for the operation under test, leaving fixture setup audited normally.
            doThrow(new DataAccessResourceFailureException("simulated audit failure")).when(audits)
                    .insert(argThat((OperationAuditDO row) -> row != null && operation.equals(row.getOperation())));
            if (operation.startsWith("LEAVE")) {
                var leave = leave();
                if (operation.equals("LEAVE_REVIEW")) leaves.reviewLeave(manager(leave),leave.id(),new LeaveReviewReqDTO("APPROVED",""));
            } else if (operation.startsWith("SCALE")) {
                var scale = scales.importScale(admin.id(),AssessmentFixtures.scale(marker,"ORIGINAL-TEST-FIXTURE"));
                if (!operation.equals("SCALE_IMPORT")) scales.publishScale(admin.id(),scale.id(),new ScalePublishReqDTO(true,true,true));
                if (operation.equals("SCALE_ARCHIVE")) scales.archiveScale(admin.id(),scale.id());
            } else if (operation.startsWith("GRAPH")) {
                try {
                    var graph = graphs.importVersion(admin.id(),graph(marker));
                    if (!operation.equals("GRAPH_IMPORT")) graphs.publishVersion(admin.id(),graph.id());
                    if (operation.equals("GRAPH_ARCHIVE")) graphs.archiveVersion(admin.id(),graph.id());
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException(exception); }
            } else {
                attendance.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(0,0),LocalTime.of(23,59)));
            }
            return null;
        })).isInstanceOf(DataAccessResourceFailureException.class);
        reset(audits);
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM sys_user WHERE user_name = ?",Long.class,marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM assessment_scale WHERE version = ?",Long.class,marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM knowledge_graph_version WHERE version = ?",Long.class,marker)).isZero();
        assertThat(attendance.getRule()).isEqualTo(originalRule);
    }
}
