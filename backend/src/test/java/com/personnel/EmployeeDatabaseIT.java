package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.mapper.EmployeeProfileMapper;
import com.personnel.mapper.UserMapper;
import com.personnel.service.OrganizationService;
import com.personnel.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt in with -Dtest=EmployeeDatabaseIT. Requires the existing local MySQL schema; all test records roll back. */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@Transactional
class EmployeeDatabaseIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired OrganizationService organizations;
    @Autowired UserMapper userMapper;
    @Autowired EmployeeProfileMapper profiles;
    @Autowired JwtUtil jwtUtil;
    private String cachedAdminToken;
    String adminToken() {
        if (cachedAdminToken == null) {
            var admin = users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "测试管理员", null, "ADMIN", null, null));
            cachedAdminToken = "Bearer " + jwtUtil.createToken(admin.id(), "ADMIN");
        }
        return cachedAdminToken;
    }
    String unique() { return "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }

    @Test
    void databaseRoundTripFilteringPaginationAndNullClearing() throws Exception {
        String marker = unique();
        var department = organizations.addDepartment(new DepartmentSaveReqDTO(marker, marker, null, 0));
        var position = organizations.addPosition(new PositionSaveReqDTO(marker, marker, department.id(), null));
        var user = users.createUser(new AdminUserCreateReqDTO(marker, "TestOnly123", marker, "123", "EMPLOYEE", null, null));
        var request = new EmployeeUpdateReqDTO("档案验收", "13800138000", department.id(), position.id(), LocalDate.of(2025, 1, 1), "RESIGNED");
        mvc.perform(put("/api/admin/employees/{id}", user.id()).header("Authorization", adminToken()).contentType("application/json").content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/admin/employees").header("Authorization", adminToken()).param("keyword", marker).param("departmentId", department.id().toString()).param("employmentStatus", "RESIGNED").param("pageSize", "1"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].departmentName").value(marker)).andExpect(jsonPath("$.data.records[0].positionName").value(marker))
                .andExpect(jsonPath("$.data.records[0].hireDate").value("2025-01-01")).andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/employees").header("Authorization", adminToken()).param("keyword", marker).param("pageNum", "2").param("pageSize", "1"))
                .andExpect(jsonPath("$.data.records").isEmpty()).andExpect(jsonPath("$.data.total").value(1));
        var cleared = new EmployeeUpdateReqDTO("档案验收", "", null, null, null, "ACTIVE");
        mvc.perform(put("/api/admin/employees/{id}", user.id()).header("Authorization", adminToken()).contentType("application/json").content(json.writeValueAsBytes(cleared)))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(userMapper.selectById(user.id()).getDepartmentId()).isNull();
        assertThat(userMapper.selectById(user.id()).getPositionId()).isNull();
        assertThat(profiles.selectById(user.id()).getHireDate()).isNull();
        assertThat(profiles.selectById(user.id()).getEmploymentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void invalidInputAndRelationshipsDoNotChangeEmployee() throws Exception {
        String marker = unique();
        var user = users.createUser(new AdminUserCreateReqDTO(marker, "TestOnly123", marker, null, "EMPLOYEE", null, null));
        mvc.perform(get("/api/admin/employees").header("Authorization", adminToken()).param("pageSize", "101")).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/admin/employees").header("Authorization", adminToken()).param("employmentStatus", "OTHER")).andExpect(jsonPath("$.code").value(400));
        var future = new EmployeeUpdateReqDTO("测试", null, null, null, LocalDate.now().plusDays(1), "ACTIVE");
        mvc.perform(put("/api/admin/employees/{id}", user.id()).header("Authorization", adminToken()).contentType("application/json").content(json.writeValueAsBytes(future))).andExpect(jsonPath("$.code").value(400));
        var invalid = new EmployeeUpdateReqDTO("测试", null, Long.MAX_VALUE, null, null, "ACTIVE");
        mvc.perform(put("/api/admin/employees/{id}", user.id()).header("Authorization", adminToken()).contentType("application/json").content(json.writeValueAsBytes(invalid))).andExpect(jsonPath("$.code").value(400));
        assertThat(userMapper.selectById(user.id()).getRealName()).isEqualTo(marker);
        assertThatThrownBy(() -> users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "测试", null, "EMPLOYEE", Long.MAX_VALUE, null)))
                .hasMessage("所属部门不存在或已停用");
    }

    @Test
    void employeeEndpointsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/employees")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"EMPLOYEE", "DEPARTMENT_MANAGER"}) {
            var account = users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "测试员工", null, role, null, null));
            String token = "Bearer " + jwtUtil.createToken(account.id(), role);
            mvc.perform(get("/api/admin/employees").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/employees/1").header("Authorization", token).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }
}
