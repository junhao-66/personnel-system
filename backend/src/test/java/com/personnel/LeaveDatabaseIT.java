package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.mapper.UserMapper;
import com.personnel.service.*;
import com.personnel.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real MySQL integration tests. All business data rolls back; additive schema remains. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveDatabaseIT {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired LeaveService leaves;
    @Autowired UserMapper userMapper;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    final LocalDateTime start = LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);
    String unique() { return "lv" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    UserVO account(String role, Long department) { return users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "请假测试", null, role, department, null)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode()); }
    record Fixture(UserVO manager, UserVO employee, Long departmentId) { }
    Fixture fixture() {
        var manager = account("DEPARTMENT_MANAGER", null);
        var department = organizations.addDepartment(new DepartmentSaveReqDTO("请假测试部门", unique(), manager.id(), 0));
        return new Fixture(manager, account("EMPLOYEE", department.id()), department.id());
    }
    LeaveCreateReqDTO request(LocalDateTime begin) { return new LeaveCreateReqDTO("事假", begin, begin.plusHours(1), "个人事务"); }

    @Test void submissionReviewAndPersonalResultRoundTrip() throws Exception {
        var f = fixture(); var stranger = fixture();
        var result = mvc.perform(post("/api/leave").header("Authorization", bearer(f.employee())).contentType("application/json")
                .content(json.writeValueAsBytes(request(start)))).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(f.employee().id())).andExpect(jsonPath("$.data.status").value("PENDING")).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/leave/reviews").header("Authorization", bearer(stranger.manager()))).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(put("/api/leave/" + id + "/review").header("Authorization", bearer(stranger.manager())).contentType("application/json").content("{\"status\":\"APPROVED\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/api/leave/" + id + "/review").header("Authorization", bearer(f.manager())).contentType("application/json").content("{\"status\":\"APPROVED\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(put("/api/leave/" + id + "/review").header("Authorization", bearer(f.manager())).contentType("application/json").content("{\"status\":\"REJECTED\",\"reviewComment\":\"重复处理\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/leave").header("Authorization", bearer(f.employee())).param("userId", stranger.employee().id().toString()))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.records[0].reviewerId").value(f.manager().id())).andExpect(jsonPath("$.data.records[0].reviewedAt").exists());
        mvc.perform(get("/api/leave").header("Authorization", bearer(stranger.employee()))).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void overlapAndRejectedResubmissionUseHalfOpenIntervals() throws Exception {
        var f = fixture(); var first = leaves.addLeave(f.employee().id(), request(start));
        assertThatThrownBy(() -> leaves.addLeave(f.employee().id(), request(start.plusMinutes(30)))).hasMessageContaining("重叠");
        assertThat(leaves.addLeave(f.employee().id(), request(start.plusHours(1))).id()).isNotNull();
        mvc.perform(put("/api/leave/" + first.id() + "/review").header("Authorization", bearer(f.manager())).contentType("application/json").content("{\"status\":\"REJECTED\",\"reviewComment\":\"   \"}"))
                .andExpect(jsonPath("$.code").value(400));
        leaves.reviewLeave(f.manager().id(), first.id(), new LeaveReviewReqDTO("REJECTED", "请调整日期"));
        assertThat(leaves.addLeave(f.employee().id(), request(start)).id()).isNotEqualTo(first.id());
    }

    @Test void changedManagerAndDepartmentImmediatelyChangePendingScope() {
        var f = fixture(); var second = fixture(); var value = leaves.addLeave(f.employee().id(), request(start));
        leaves.configureManager(f.departmentId(), second.manager().id());
        assertThat(leaves.listForReview(f.manager().id(), new LeaveQueryReqDTO()).total()).isZero();
        assertThat(leaves.listForReview(second.manager().id(), new LeaveQueryReqDTO()).total()).isEqualTo(1);
        var entity = userMapper.selectById(f.employee().id()); entity.setDepartmentId(second.departmentId()); userMapper.updateById(entity);
        leaves.configureManager(f.departmentId(), f.manager().id());
        assertThatThrownBy(() -> leaves.reviewLeave(f.manager().id(), value.id(), new LeaveReviewReqDTO("APPROVED", ""))).hasMessageContaining("审批范围");
        leaves.reviewLeave(second.manager().id(), value.id(), new LeaveReviewReqDTO("APPROVED", ""));
    }

    @Test void rolesMissingApproverAndInvalidParametersAreProtected() throws Exception {
        var f = fixture(); var admin = account("ADMIN", null);
        mvc.perform(get("/api/leave/reviews").header("Authorization", bearer(f.employee()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/leave/reviews").header("Authorization", bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/leave")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leave").header("Authorization", bearer(f.employee())).param("pageSize", "101")).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/leave").header("Authorization", bearer(f.employee())).param("status", "UNKNOWN")).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/leave").header("Authorization", bearer(f.employee())).contentType("application/json").content("{}" )).andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/api/admin/departments/" + f.departmentId() + "/manager").header("Authorization", bearer(f.manager())).contentType("application/json").content("{\"managerUserId\":" + f.manager().id() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/departments/" + f.departmentId() + "/manager").header("Authorization", bearer(admin)).contentType("application/json").content("{\"managerUserId\":" + f.manager().id() + "}"))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(leaves.getOptions(f.manager().id()).unavailableReason()).isNotBlank();
        var self = userMapper.selectById(f.manager().id()); self.setDepartmentId(f.departmentId()); userMapper.updateById(self);
        assertThat(leaves.getOptions(f.manager().id()).unavailableReason()).contains("本人不能自批");
        organizations.setDepartmentEnabled(f.departmentId(), false);
        assertThatThrownBy(() -> leaves.addLeave(f.employee().id(), request(start))).hasMessageContaining("所属部门");
    }

    @Test void pendingAndDisabledAccountsCannotAccessLeave() throws Exception {
        var f = fixture(); String token = bearer(f.employee()); var entity = userMapper.selectById(f.employee().id());
        entity.setAuditStatus("PENDING"); userMapper.updateById(entity);
        mvc.perform(post("/api/leave").header("Authorization", token).contentType("application/json").content(json.writeValueAsBytes(request(start))))
                .andExpect(status().isUnauthorized());
        entity.setAuditStatus("APPROVED"); entity.setUserStatus("DISABLED"); userMapper.updateById(entity);
        mvc.perform(get("/api/leave").header("Authorization", token)).andExpect(status().isUnauthorized());
    }
}
