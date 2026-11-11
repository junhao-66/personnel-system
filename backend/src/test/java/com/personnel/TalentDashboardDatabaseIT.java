package com.personnel;

import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the existing local MySQL schema; all synthetic accounts, results and organization changes roll back. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class TalentDashboardDatabaseIT {
    @Autowired TalentDashboardService dashboard;
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired EmployeeService employees;
    @Autowired AssessmentScaleService scales;
    @Autowired AssessmentService assessments;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    String unique() { return "td" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    UserVO account(String role, Long departmentId, Long positionId) { return users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "看板验收", null, role, departmentId, positionId)); }
    DepartmentVO department() { String value = unique(); return organizations.addDepartment(new DepartmentSaveReqDTO(value, value, null, 0)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode()); }
    // Fixture writes use JDBC, outside MyBatis cache invalidation; each following read must hit the actual database.
    void updateFixture(String sql, Object... args) { jdbc.update(sql, args); sqlSession.clearCache(); }
    TalentQueryReqDTO query(Long departmentId) { var q = new TalentQueryReqDTO(); q.setDepartmentId(departmentId); return q; }
    AssessmentScaleVO scale(UserVO admin) {
        var value = scales.importScale(admin.id(), AssessmentFixtures.scale(unique(), "ORIGINAL-TEST-FIXTURE"));
        scales.publishScale(admin.id(), value.id(), new ScalePublishReqDTO(true, true, true)); return value;
    }
    AssessmentResultVO submit(UserVO user, AssessmentScaleVO scale, String type) {
        var draft = assessments.startAssessment(user.id(), new AssessmentStartReqDTO(scale.id(), true));
        return assessments.submitAssessment(user.id(), draft.id(), new AssessmentAnswersReqDTO(0, AssessmentFixtures.answers(type)));
    }
    @Test void repeatedAssessmentsUseLatestTypeAndDraftsDoNotInflateParticipation() {
        var admin = account("ADMIN", null, null); var dep = department(); var scale = scale(admin);
        var first = account("EMPLOYEE", dep.id(), null); var second = account("DEPARTMENT_MANAGER", dep.id(), null);
        var third = account("ADMIN", dep.id(), null); account("EMPLOYEE", dep.id(), null);
        submit(first, scale, "INTJ"); var latest = submit(first, scale, "ENFP"); submit(second, scale, "INTJ");
        assessments.startAssessment(third.id(), new AssessmentStartReqDTO(scale.id(), true));
        var q = query(dep.id()); var result = dashboard.getDashboard(admin.id(), q);
        assertThat(result.employeeCount()).isEqualTo(4); assertThat(result.completedCount()).isEqualTo(2);
        assertThat(result.notCompletedCount()).isEqualTo(2); assertThat(result.assessmentCount()).isEqualTo(3);
        assertThat(result.completionRate()).isEqualByComparingTo("50");
        assertThat(result.typeDistribution().stream().filter(t -> t.typeCode().equals("INTJ")).findFirst().orElseThrow().employeeCount()).isEqualTo(1);
        assertThat(result.typeDistribution().stream().filter(t -> t.typeCode().equals("ENFP")).findFirst().orElseThrow().percent()).isEqualByComparingTo("50");
        var detail = dashboard.getEmployee(admin.id(), first.id()); assertThat(detail.latestResultId()).isEqualTo(latest.id()); assertThat(detail.assessmentCount()).isEqualTo(2);
        assertThat(dashboard.listEmployees(admin.id(), q).total()).isEqualTo(result.employeeCount());
        q.setAssessmentStatus("COMPLETED"); assertThat(dashboard.listEmployees(admin.id(), q).total()).isEqualTo(result.completedCount());
        q.setTypeCode("ENFP"); assertThat(dashboard.listEmployees(admin.id(), q).records()).extracting(TalentEmployeeVO::userId).containsExactly(first.id());
        q.setTypeCode(null); q.setAssessmentStatus("NOT_COMPLETED"); assertThat(dashboard.listEmployees(admin.id(), q).total()).isEqualTo(result.notCompletedCount());
        q.setTypeCode("INTJ"); assertThat(dashboard.listEmployees(admin.id(), q).total()).isZero();
    }
    @Test void organizationEmploymentAndAuditFiltersShareTheSameDenominatorAndPageCount() {
        var admin = account("ADMIN", null, null); var dep = department(); String marker = unique();
        var position = organizations.addPosition(new PositionSaveReqDTO(marker, marker, dep.id(), null));
        var active = account("EMPLOYEE", dep.id(), position.id());
        var resigned = account("EMPLOYEE", dep.id(), position.id()); var pending = account("EMPLOYEE", dep.id(), position.id());
        var rejected = account("EMPLOYEE", dep.id(), position.id()); var disabled = account("EMPLOYEE", dep.id(), null);
        employees.updateEmployee(resigned.id(), new EmployeeUpdateReqDTO("离职验收", null, dep.id(), position.id(), LocalDate.of(2025,1,1), "RESIGNED"));
        updateFixture("UPDATE sys_user SET audit_status='PENDING' WHERE id=?", pending.id());
        updateFixture("UPDATE sys_user SET audit_status='REJECTED' WHERE id=?", rejected.id());
        updateFixture("UPDATE sys_user SET user_status='DISABLED' WHERE id=?", disabled.id());
        var q = query(dep.id()); assertThat(dashboard.getDashboard(admin.id(), q).employeeCount()).isEqualTo(2);
        q.setPositionId(position.id()); assertThat(dashboard.listEmployees(admin.id(), q).records()).extracting(TalentEmployeeVO::userId).containsExactly(active.id());
        q.setEmploymentStatus("RESIGNED"); assertThat(dashboard.getDashboard(admin.id(), q).employeeCount()).isEqualTo(1);
        q.setEmploymentStatus("ALL"); q.setPageSize(1); q.setPageNum(2);
        var page = dashboard.listEmployees(admin.id(), q); assertThat(page.total()).isEqualTo(2); assertThat(page.records()).hasSize(1);
        assertThat(dashboard.getDashboard(admin.id(), q).employeeCount()).isEqualTo(2);
        q.setPageNum(3); assertThat(dashboard.listEmployees(admin.id(), q).records()).isEmpty();
        assertThatThrownBy(() -> dashboard.getEmployee(admin.id(), pending.id())).hasMessageContaining("尚未通过审核");
    }
    @Test void emptyScopeNullRatesUnassignedAndDisabledOrganizationsRemainQueryable() throws Exception {
        var admin = account("ADMIN", null, null); var dep = department(); var q = query(dep.id());
        assertThat(dashboard.getDashboard(admin.id(), q).completionRate()).isNull();
        mvc.perform(get("/api/admin/talent/dashboard").header("Authorization", bearer(admin)).param("departmentId", dep.id().toString()))
                .andExpect(jsonPath("$.data.employeeCount").value(0)).andExpect(jsonPath("$.data.completionRate").value(org.hamcrest.Matchers.nullValue()));
        var user = account("EMPLOYEE", dep.id(), null); organizations.setDepartmentEnabled(dep.id(), false);
        assertThat(dashboard.listEmployees(admin.id(), q).total()).isEqualTo(1);
        var unassigned = account("EMPLOYEE", null, null); q.setDepartmentId(0L); q.setPositionId(0L);
        assertThat(dashboard.listEmployees(admin.id(), q).records()).extracting(TalentEmployeeVO::departmentId).containsOnlyNulls();
        assertThat(dashboard.getEmployee(admin.id(), unassigned.id()).departmentName()).isEqualTo("未分配部门");
        assertThat(dashboard.getEmployee(admin.id(), user.id()).departmentId()).isEqualTo(dep.id());
    }
    @Test void archivedScalesStayInTotalsAndLatestIsOrderedBySubmissionTimeThenId() {
        var admin = account("ADMIN", null, null); var dep = department(); var scale = scale(admin); var user = account("EMPLOYEE", dep.id(), null);
        var first = submit(user, scale, "INTJ"); var second = submit(user, scale, "ENFP");
        updateFixture("UPDATE assessment_session SET submitted_at='2026-10-29 10:00:00' WHERE id IN (?, ?)", first.id(), second.id());
        assertThat(dashboard.getEmployee(admin.id(), user.id()).latestResultId()).isEqualTo(second.id());
        updateFixture("UPDATE assessment_session SET submitted_at='2026-10-29 11:00:00' WHERE id=?", first.id());
        assertThat(dashboard.getEmployee(admin.id(), user.id()).latestResultId()).isEqualTo(first.id());
        scales.archiveScale(admin.id(), scale.id()); assertThat(dashboard.getDashboard(admin.id(), query(dep.id())).assessmentCount()).isEqualTo(2);
    }
    @Test void malformedCompletedSnapshotsDoNotBreakDashboardOrBecomeValidParticipation() {
        var admin = account("ADMIN", null, null); var dep = department(); var scale = scale(admin); var user = account("EMPLOYEE", dep.id(), null);
        var result = submit(user, scale, "INTJ");
        for (String invalid : new String[]{"{", "{}", "{\"typeCode\":\"intj\"}", "{\"typeCode\":\"OTHER\"}"}) {
            updateFixture("UPDATE assessment_session SET result_json=? WHERE id=?", invalid, result.id());
            var statistics = dashboard.getDashboard(admin.id(), query(dep.id()));
            assertThat(statistics.employeeCount()).isEqualTo(1); assertThat(statistics.completedCount()).isZero();
            assertThat(dashboard.listEmployees(admin.id(), query(dep.id())).records().get(0).latestResultId()).isNull();
        }
    }
    @Test void httpPermissionsAndInvalidQueriesAreRejectedForAllThreeEndpoints() throws Exception {
        var admin = account("ADMIN", null, null);
        for (String path : new String[]{"/api/admin/talent/dashboard", "/api/admin/talent/employees", "/api/admin/talent/employees/1"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : new String[]{"EMPLOYEE", "DEPARTMENT_MANAGER"}) mvc.perform(get(path).header("Authorization", bearer(account(role,null,null)))).andExpect(status().isForbidden());
        }
        var pending = account("ADMIN", null, null); updateFixture("UPDATE sys_user SET audit_status='PENDING' WHERE id=?", pending.id());
        mvc.perform(get("/api/admin/talent/dashboard").header("Authorization", bearer(pending))).andExpect(status().isUnauthorized());
        for (String[] invalid : new String[][]{{"departmentId","-1"},{"positionId","-1"},{"pageNum","0"},{"pageSize","101"},{"employmentStatus","INVALID"},{"assessmentStatus","INVALID"},{"typeCode","intj"}}) {
            mvc.perform(get("/api/admin/talent/employees").header("Authorization", bearer(admin)).param(invalid[0],invalid[1])).andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(get("/api/admin/talent/employees").header("Authorization", bearer(admin)).param("departmentId", Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(get("/api/admin/talent/employees/"+admin.id()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andExpect(jsonPath("$.data.phone").doesNotExist()).andExpect(jsonPath("$.data.answers").doesNotExist());
    }
}
