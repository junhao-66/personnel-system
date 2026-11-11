package com.personnel;

import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:attendance-leave-schema.sql,classpath:audit-schema.sql",
        "spring.sql.init.data-locations=optional:classpath:attendance-leave-no-seed.sql"})
@AutoConfigureMockMvc
@Transactional
class AttendanceLeaveDatabaseIT {
    @Autowired AttendanceService attendance;
    @Autowired LeaveService leaves;
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @MockBean Clock clock;
    final LocalDate day = LocalDate.of(2026,10,28);
    void time(int hour, int minute) { when(clock.instant()).thenReturn(day.atTime(hour,minute).atZone(ZoneId.of("Asia/Shanghai")).toInstant()); }
    @BeforeEach void setup() { when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai")); time(8,0); }
    String unique() { return "al" + UUID.randomUUID().toString().replace("-", "").substring(0,16); }
    UserVO account(String role, Long department) { return users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "联动验收", null, role, department, null)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode()); }
    record Fixture(UserVO manager, UserVO employee, Long department) { }
    Fixture fixture() {
        var manager = account("DEPARTMENT_MANAGER",null);
        var department = organizations.addDepartment(new DepartmentSaveReqDTO(unique(),unique(),manager.id(),0));
        var employee = account("EMPLOYEE",department.id());
        jdbc.update("UPDATE sys_user SET create_time=?, reviewed_at=? WHERE id=?", day.minusDays(1).atStartOfDay(),day.minusDays(1).atStartOfDay(),employee.id());
        attendance.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(9,0),LocalTime.of(18,0)));
        return new Fixture(manager,employee,department.id());
    }
    LeaveVO leave(UserVO user, int from, int to) { return leaves.addLeave(user.id(),new LeaveCreateReqDTO("事假",day.atTime(from,0),day.atTime(to,0),"测试请假")); }
    AttendanceQueryReqDTO query() { var query = new AttendanceQueryReqDTO(); query.setStartDate(day); query.setEndDate(day); query.setIncludeMissing(true); return query; }

    @Test void pendingDoesNotExcuseLateButApprovalImmediatelyReevaluatesWithoutChangingRawPunch() {
        var f = fixture(); var leave = leave(f.employee(),9,13);
        time(13,0); assertThat(attendance.clockIn(f.employee().id()).late()).isTrue();
        leaves.reviewLeave(f.manager().id(),leave.id(),new LeaveReviewReqDTO("APPROVED",""));
        var today = attendance.getToday(f.employee().id()); assertThat(today.record().late()).isFalse();
        assertThat(today.dayStatus().approvedLeaveMinutes()).isEqualTo(240);
        assertThat(jdbc.queryForObject("SELECT clock_in_time FROM attendance_record WHERE user_id=? AND attendance_date=?",LocalDateTime.class,f.employee().id(),day)).isEqualTo(day.atTime(13,0));
        assertThat(jdbc.queryForObject("SELECT is_late FROM attendance_record WHERE user_id=? AND attendance_date=?",Integer.class,f.employee().id(),day)).isEqualTo(1);
    }
    @Test void fullLeaveAndMissingDaysAppearWithoutInsertingFakePunches() throws Exception {
        var f = fixture(); var leave = leave(f.employee(),9,18);
        leaves.reviewLeave(f.manager().id(),leave.id(),new LeaveReviewReqDTO("APPROVED","")); time(19,0);
        var page = attendance.listMine(f.employee().id(),query()); assertThat(page.total()).isEqualTo(1);
        var record = page.records().get(0).attendance(); assertThat(record.status()).isEqualTo("APPROVED_LEAVE"); assertThat(record.id()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM attendance_record WHERE user_id=?",Long.class,f.employee().id())).isZero();
        mvc.perform(get("/api/attendance/records").header("Authorization",bearer(f.employee())).param("includeMissing","true").param("startDate",day.toString()).param("endDate",day.toString()).param("userId",f.manager().id().toString()))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].attendance.userId").value(f.employee().id()));
    }
    @Test void missingBoundaryPaginationAndFutureDateProtection() {
        var f=fixture(); time(18,0);
        assertThat(attendance.listMine(f.employee().id(),query()).records().get(0).attendance().missingIn()).isFalse();
        time(18,1); assertThat(attendance.listMine(f.employee().id(),query()).records().get(0).attendance().status()).isEqualTo("MISSING_BOTH");
        var query=query(); query.setStartDate(day.plusDays(1)); query.setEndDate(day.plusDays(2)); assertThat(attendance.listMine(f.employee().id(),query).records()).isEmpty();
        query=query(); query.setPageNum(2); assertThat(attendance.listMine(f.employee().id(),query).records()).isEmpty();
    }
    @Test void rejectedLeaveDoesNotExcuseEarlyDeparture() {
        var f=fixture(); var leave=leave(f.employee(),15,18); leaves.reviewLeave(f.manager().id(),leave.id(),new LeaveReviewReqDTO("REJECTED","需要到岗"));
        time(9,0); attendance.clockIn(f.employee().id()); time(15,0);
        assertThat(attendance.clockOut(f.employee().id()).early()).isTrue();
    }
    @Test void managerSelfLeaveRoutesOnlyToConfiguredOtherManagerAndHandoverIsProtected() throws Exception {
        var f=fixture(); var other=account("DEPARTMENT_MANAGER",null); var stranger=account("DEPARTMENT_MANAGER",null);
        jdbc.update("UPDATE sys_user SET department_id=? WHERE id=?",f.department(),f.manager().id());
        assertThat(leaves.getOptions(f.manager().id()).unavailableReason()).contains("本人不能自批");
        assertThatThrownBy(() -> leaves.configureSelfApprover(f.department(),f.manager().id())).hasMessageContaining("不能");
        leaves.configureSelfApprover(f.department(),other.id());
        var value=leave(f.manager(),9,10);
        assertThat(leaves.listForReview(f.manager().id(),new LeaveQueryReqDTO()).total()).isZero();
        assertThat(leaves.listForReview(other.id(),new LeaveQueryReqDTO()).total()).isEqualTo(1);
        assertThat(leaves.listForReview(stranger.id(),new LeaveQueryReqDTO()).total()).isZero();
        assertThatThrownBy(() -> leaves.reviewLeave(f.manager().id(),value.id(),new LeaveReviewReqDTO("APPROVED",""))).hasMessageContaining("审批范围");
        var admin=account("ADMIN",null);
        assertThatThrownBy(() -> users.updateAccess(admin.id(),other.id(),new UserAccessReqDTO("EMPLOYEE","ENABLED"))).hasMessageContaining("移交");
        leaves.configureSelfApprover(f.department(),stranger.id());
        assertThatThrownBy(() -> leaves.reviewLeave(other.id(),value.id(),new LeaveReviewReqDTO("APPROVED",""))).hasMessageContaining("审批范围");
        leaves.reviewLeave(stranger.id(),value.id(),new LeaveReviewReqDTO("APPROVED",""));
        mvc.perform(put("/api/admin/departments/"+f.department()+"/self-approver").header("Authorization",bearer(f.employee())).contentType("application/json").content("{\"managerUserId\":null}"))
                .andExpect(status().isForbidden());
    }
    @Test void dailyViewOmitsPreEmploymentAndNonActiveSyntheticDays() {
        var f=fixture(); time(19,0);
        jdbc.update("UPDATE employee_profile SET hire_date=? WHERE user_id=?",day.plusDays(1),f.employee().id());
        assertThat(attendance.listMine(f.employee().id(),query()).records()).isEmpty();
        jdbc.update("UPDATE employee_profile SET hire_date=NULL, employment_status='RESIGNED' WHERE user_id=?",f.employee().id());
        assertThat(attendance.listMine(f.employee().id(),query()).records()).isEmpty();
    }
}
