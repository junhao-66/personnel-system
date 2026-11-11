package com.personnel;

import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.entity.OperationAuditDO;
import com.personnel.mapper.OperationAuditMapper;
import com.personnel.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@Transactional
class OrganizationEditingDatabaseIT {
    @Autowired OrganizationService organizations;
    @Autowired UserService users;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @SpyBean OperationAuditMapper audits;
    @AfterEach void cleanup() { reset(audits); }
    String unique() { return "oe" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    String token(String role) {
        var user = users.createUser(new AdminUserCreateReqDTO(unique(), "TestOnly123", "组织验收", null, role, null, null));
        return "Bearer " + jwt.createToken(user.id(), role);
    }
    @Test void departmentUpdatePersistsWithAuditAndNoOpIsIdempotent() throws Exception {
        String admin = token("ADMIN");
        var department = organizations.addDepartment(new DepartmentSaveReqDTO(unique(), unique(), null, 0));
        var request = new DepartmentSaveReqDTO("更新部门", unique(), null, 7);
        for (int i = 0; i < 2; i++) mvc.perform(put("/api/admin/departments/" + department.id())
                .header("Authorization", admin).contentType("application/json").content(json.writeValueAsBytes(request)))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.sortOrder").value(7));
        assertThat(jdbc.queryForObject("SELECT department_name FROM sys_department WHERE id=?", String.class, department.id())).isEqualTo("更新部门");
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM operation_audit WHERE operation='DEPARTMENT_UPDATE' AND target_id=?", Long.class, department.id())).isEqualTo(1);
        mvc.perform(get("/api/admin/operation-audits").header("Authorization", admin).param("operation", "DEPARTMENT_UPDATE"))
                .andExpect(jsonPath("$.code").value(200));
    }
    @Test void positionDescriptionCanBeClearedButDepartmentCannotChange() throws Exception {
        String admin = token("ADMIN");
        var department = organizations.addDepartment(new DepartmentSaveReqDTO(unique(), unique(), null, 0));
        var position = organizations.addPosition(new PositionSaveReqDTO(unique(), unique(), department.id(), "说明"));
        mvc.perform(put("/api/admin/positions/" + position.id()).header("Authorization", admin).contentType("application/json")
                .content(json.writeValueAsBytes(new PositionSaveReqDTO("更新岗位", position.positionCode(), department.id(), ""))))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(jdbc.queryForObject("SELECT description FROM sys_position WHERE id=?", String.class, position.id())).isEmpty();
        mvc.perform(put("/api/admin/positions/" + position.id()).header("Authorization", admin).contentType("application/json")
                .content(json.writeValueAsBytes(new PositionSaveReqDTO("更新岗位", position.positionCode(), null, ""))))
                .andExpect(jsonPath("$.code").value(400));
        assertThat(jdbc.queryForObject("SELECT department_id FROM sys_position WHERE id=?", Long.class, position.id())).isEqualTo(department.id());
    }
    @Test void endpointsRejectNonAdminAndInvalidInputs() throws Exception {
        var request = new DepartmentSaveReqDTO("有效名称", "VALID", null, 0);
        mvc.perform(put("/api/admin/departments/1").contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(status().isUnauthorized());
        for (String role : new String[]{"EMPLOYEE", "DEPARTMENT_MANAGER"}) {
            String bearer = token(role);
            mvc.perform(put("/api/admin/departments/1").header("Authorization", bearer).contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/positions/1").header("Authorization", bearer).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        }
        String admin = token("ADMIN");
        mvc.perform(put("/api/admin/departments/1").header("Authorization", admin).contentType("application/json").content("{\"departmentName\":\" \",\"departmentCode\":\"D\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/api/admin/positions/9223372036854775807").header("Authorization", admin).contentType("application/json")
                .content(json.writeValueAsBytes(new PositionSaveReqDTO("名称", "CODE", null, "")))).andExpect(jsonPath("$.code").value(400));
    }
}
