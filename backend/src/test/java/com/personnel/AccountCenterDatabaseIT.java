package com.personnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.util.JwtUtil;
import com.personnel.dto.req.*;
import com.personnel.service.UserService;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print = MockMvcPrint.NONE) @Transactional
class AccountCenterDatabaseIT {
    @Autowired UserService users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    UserVO user(String role) { return users.createUser(new AdminUserCreateReqDTO("self"+UUID.randomUUID().toString().replace("-", "").substring(0,16), "Fixture123", "账号中心验收", null, role, null, null)); }
    String bearer(UserVO user) { return "Bearer " + jwt.createToken(user.id(), user.roleCode(), user.authVersion()); }
    String password(String old, String next) throws Exception { return json.writeValueAsString(new AccountPasswordReqDTO(old, next)); }
    @Test void allThreeRolesCanMaintainOnlyTheirOwnContact() throws Exception {
        var other = user("EMPLOYEE");
        for (String role : new String[]{"EMPLOYEE", "ADMIN", "DEPARTMENT_MANAGER"}) {
            var owner = user(role);
            mvc.perform(put("/api/user/me/contact").header("Authorization", bearer(owner)).contentType("application/json")
                    .content("{\"phone\":\"13900000000\",\"userId\":"+other.id()+",\"roleCode\":\"ADMIN\"}"))
                    .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").value(owner.id()))
                    .andExpect(jsonPath("$.data.roleCode").value(role)).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
            assertThat(users.getById(owner.id()).phone()).isEqualTo("13900000000");
            mvc.perform(put("/api/user/me/contact").header("Authorization", bearer(owner)).contentType("application/json").content("{\"phone\":\"\"}"))
                    .andExpect(jsonPath("$.code").value(200));
            assertThat(users.getById(owner.id()).phone()).isNull();
        }
        assertThat(users.getById(other.id()).phone()).isNull();
    }
    @Test void invalidContactAndUnauthenticatedRequestsAreRejected() throws Exception {
        var owner = user("EMPLOYEE");
        mvc.perform(put("/api/user/me/contact").contentType("application/json").content("{\"phone\":\"13900000000\"}" )).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/user/me/contact").header("Authorization",bearer(owner)).contentType("application/json").content("{\"phone\":\"<script>\"}"))
                .andExpect(jsonPath("$.code").value(400));
        assertThat(users.getById(owner.id()).phone()).isNull();
    }
    @Test void passwordChangeInvalidatesAllOldTokensAndAllowsOnlyNewPassword() throws Exception {
        var owner = user("EMPLOYEE"); var other = user("EMPLOYEE");
        String oldToken = bearer(owner);
        String anotherToken = bearer(owner);
        mvc.perform(put("/api/user/me/password").header("Authorization",oldToken).contentType("application/json").content(password("Fixture123", "Changed123!")))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        for (String token : new String[]{oldToken, anotherToken}) mvc.perform(get("/api/user/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        assertThat(users.getById(owner.id()).authVersion()).isEqualTo(owner.authVersion()+1);
        assertThatThrownBy(() -> users.login(new UserLoginReqDTO(owner.userName(),"Fixture123"))).hasMessageContaining("用户名或密码错误");
        var loggedIn = users.login(new UserLoginReqDTO(owner.userName(),"Changed123!"));
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer "+loggedIn.accessToken())).andExpect(jsonPath("$.code").value(200));
        assertThat(users.login(new UserLoginReqDTO(other.userName(),"Fixture123")).user().id()).isEqualTo(other.id());
        String fields = jdbc.queryForObject("SELECT changed_fields FROM operation_audit WHERE operation='USER_PASSWORD_CHANGE' AND target_id=?",String.class,owner.id());
        assertThat(fields).contains("credentialUpdated").doesNotContain("Fixture123","Changed123", "passwordHash");
    }
    @Test void wrongCurrentSameShortAndOversizedUnicodePasswordsDoNotInvalidateLogin() throws Exception {
        var owner = user("EMPLOYEE"); String token = bearer(owner);
        for (String[] pair : new String[][]{{"wrong", "Changed123!"}, {"Fixture123","Fixture123"}, {"Fixture123","short"}, {"Fixture123","密".repeat(25)}}) {
            mvc.perform(put("/api/user/me/password").header("Authorization",token).contentType("application/json").content(password(pair[0],pair[1])))
                    .andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(get("/api/user/me").header("Authorization",token)).andExpect(jsonPath("$.code").value(200));
        assertThat(users.getById(owner.id()).authVersion()).isEqualTo(owner.authVersion());
    }
    @Test void passwordFailureLimitIsSeparateFromContactUpdates() throws Exception {
        var owner = user("EMPLOYEE"); String token=bearer(owner);
        for (int i=0;i<5;i++) mvc.perform(put("/api/user/me/password").header("Authorization",token).contentType("application/json").content(password("wrong","Changed123!")))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put("/api/user/me/password").header("Authorization",token).contentType("application/json").content(password("Fixture123","Changed123!")))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(put("/api/user/me/contact").header("Authorization",token).contentType("application/json").content("{\"phone\":\"13900000001\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }
    @Test void unchangedContactDoesNotAppendRepeatedAudit() {
        var owner=user("EMPLOYEE");
        users.updateOwnContact(owner.id(),new AccountContactReqDTO("13900000000"));
        users.updateOwnContact(owner.id(),new AccountContactReqDTO("13900000000"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit WHERE operation='USER_CONTACT' AND target_id=?",Integer.class,owner.id())).isEqualTo(1);
    }
    @Test void pendingAndDisabledAccountsCannotUseSelfService() throws Exception {
        String name="pending"+UUID.randomUUID().toString().replace("-", "").substring(0,16);
        users.register(new UserRegisterReqDTO(name,"Fixture123","待审核验收",null));
        var pending=users.listPendingUsers().stream().filter(u->name.equals(u.userName())).findFirst().orElseThrow();
        var owner=user("EMPLOYEE"); var admin=user("ADMIN"); String original=bearer(owner);
        users.updateAccess(admin.id(),owner.id(),new UserAccessReqDTO("EMPLOYEE","DISABLED"));
        for(String token:new String[]{bearer(pending),original}) {
            mvc.perform(put("/api/user/me/contact").header("Authorization",token).contentType("application/json").content("{\"phone\":\"13900000000\"}")).andExpect(status().isUnauthorized());
            mvc.perform(put("/api/user/me/password").header("Authorization",token).contentType("application/json").content(password("Fixture123","Changed123!"))).andExpect(status().isUnauthorized());
        }
    }
}
