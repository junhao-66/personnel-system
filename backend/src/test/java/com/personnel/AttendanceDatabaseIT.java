package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.mapper.AttendanceRecordMapper;
import com.personnel.mapper.UserMapper;
import com.personnel.service.AttendanceService;
import com.personnel.service.UserService;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL, additive schema init; test data and rule edits roll back. Opt in with -Dtest=AttendanceDatabaseIT. */
@SpringBootTest(properties = "spring.sql.init.mode=always")
@AutoConfigureMockMvc
@Transactional
class AttendanceDatabaseIT {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired AttendanceService attendance;
    @Autowired UserMapper userMapper;
    @Autowired AttendanceRecordMapper records;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    @MockBean Clock clock;
    final LocalDate day = LocalDate.of(2026, 10, 28);
    @BeforeEach void setUp() { when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai")); time("2026-10-28T09:00:00"); }
    void time(String localTime) { when(clock.instant()).thenReturn(LocalDateTime.parse(localTime).atZone(ZoneId.of("Asia/Shanghai")).toInstant()); }
    String unique() { return "at" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    UserVO account(String role) { return users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "考勤测试", null, role, null, null)); }
    String token(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode()); }

    @Test
    void clockRoundTripKeepsServerIdentityAndOriginalSchedule() throws Exception {
        var employee = account("EMPLOYEE"); var admin = account("ADMIN");
        attendance.updateRule(new AttendanceRuleUpdateReqDTO(LocalTime.of(9, 0), LocalTime.of(18, 0)));
        mvc.perform(post("/api/attendance/clock-out").header("Authorization", token(employee))).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", token(employee)).contentType("application/json")
                        .content("{\"userId\":1,\"clockInTime\":\"2000-01-01T00:00:00\"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.userId").value(employee.id()))
                .andExpect(jsonPath("$.data.clockInTime").value("2026-10-28T09:00:00")).andExpect(jsonPath("$.data.late").value(false));
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", token(employee))).andExpect(jsonPath("$.code").value(400));
        var changed = new AttendanceRuleUpdateReqDTO(LocalTime.of(10, 0), LocalTime.of(17, 0));
        mvc.perform(put("/api/admin/attendance/rule").header("Authorization", token(admin)).contentType("application/json").content(json.writeValueAsBytes(changed)))
                .andExpect(jsonPath("$.code").value(200));
        time("2026-10-28T17:59:59");
        mvc.perform(post("/api/attendance/clock-out").header("Authorization", token(employee)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.early").value(true)).andExpect(jsonPath("$.data.status").value("EARLY"));
        mvc.perform(post("/api/attendance/clock-out").header("Authorization", token(employee))).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/attendance/today").header("Authorization", token(employee)))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Shanghai")).andExpect(jsonPath("$.data.workEndTime").value("18:00:00"));
        assertThat(records.selectToday(employee.id(), day).getClockOutTime()).isEqualTo(day.atTime(17, 59, 59));
    }

    @Test
    void personalRecordsAreIsolatedAndManagerCannotReadOthers() throws Exception {
        var employee = account("EMPLOYEE"); var manager = account("DEPARTMENT_MANAGER"); var admin = account("ADMIN");
        attendance.clockIn(employee.id()); attendance.clockIn(manager.id());
        mvc.perform(get("/api/attendance/records").header("Authorization", token(employee)).param("userId", manager.id().toString()))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].attendance.userId").value(employee.id()))
                .andExpect(jsonPath("$.data.records[0].realName").value("考勤测试"));
        mvc.perform(get("/api/admin/attendance/records").header("Authorization", token(manager))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/attendance/rule").header("Authorization", token(employee)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/attendance/records").header("Authorization", token(admin)).param("keyword", employee.userName()))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].userName").value(employee.userName()));
        mvc.perform(get("/api/user/me").header("Authorization", token(employee)))
                .andExpect(jsonPath("$.data.id").value(employee.id())).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void inactiveAndUnapprovedAccountsLoseAccessImmediately() throws Exception {
        var employee = account("EMPLOYEE"); String bearer = token(employee);
        var entity = userMapper.selectById(employee.id()); entity.setUserStatus("DISABLED"); userMapper.updateById(entity);
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        entity.setUserStatus("ENABLED"); entity.setAuditStatus("PENDING"); userMapper.updateById(entity);
        mvc.perform(get("/api/attendance/today").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        entity.setAuditStatus("APPROVED"); entity.setRoleCode("DEPARTMENT_MANAGER"); userMapper.updateById(entity);
        mvc.perform(get("/api/user/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/attendance/clock-in")).andExpect(status().isUnauthorized());
        assertThat(records.selectToday(employee.id(), day)).isNull();
    }

    @Test
    void conditionalClockOutCannotOverwriteFirstTimestamp() {
        var employee = account("EMPLOYEE"); attendance.clockIn(employee.id());
        assertThat(records.clockOut(employee.id(), day, day.atTime(18, 0), 0)).isEqualTo(1);
        assertThat(records.clockOut(employee.id(), day, day.atTime(19, 0), 0)).isZero();
        assertThat(records.selectToday(employee.id(), day).getClockOutTime()).isEqualTo(day.atTime(18, 0));
    }

    @Test
    void invalidRulesDatesAndPageSizesReturnUnifiedErrors() throws Exception {
        var admin = account("ADMIN"); String bearer = token(admin);
        mvc.perform(put("/api/admin/attendance/rule").header("Authorization", bearer).contentType("application/json")
                        .content("{\"workStartTime\":\"18:00:00\",\"workEndTime\":\"09:00:00\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/api/admin/attendance/rule").header("Authorization", bearer).contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/attendance/records").header("Authorization", bearer).param("startDate", "2026-10-28").param("endDate", "2026-10-01"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/attendance/records").header("Authorization", bearer).param("pageSize", "101"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/attendance/records").header("Authorization", bearer).param("startDate", "bad-date"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
