package com.qctv1.iam.user.controller;

import com.qctv1.iam.auth.service.AuthService;
import com.qctv1.iam.auth.vo.UserProfileVo;
import com.qctv1.iam.common.BusinessException;
import com.qctv1.iam.common.InternalApiExceptionHandler;
import com.qctv1.iam.user.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(
        controllers = InternalUserController.class,
        properties = "spring.profiles.active=local"
)
@Import(InternalApiExceptionHandler.class)
class InternalUserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserAdminService userAdminService;

    @Test
    void getCurrentProfileWithoutHeadersReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/users/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Login required"));
    }

    @Test
    void getCurrentProfileWithHeadersReturnsProfile() throws Exception {
        UserProfileVo profileVo = new UserProfileVo();
        profileVo.setId(1L);
        profileVo.setUserName("alice");
        profileVo.setTrueName("Alice");
        profileVo.setName("Alice");
        profileVo.setRoleCode("ADMIN");
        profileVo.setRoles(List.of("ADMIN"));
        when(authService.profile(1L)).thenReturn(profileVo);

        mockMvc.perform(get("/internal/users/current")
                        .header("X-User-Id", "1")
                        .header("X-User-Name", "alice")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userName").value("alice"))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"));
    }

    @Test
    void pageUsersWithNonAdminRoleReturnsForbidden() throws Exception {
        mockMvc.perform(post("/internal/users/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNum": 1,
                                  "pageSize": 10
                                }
                                """)
                        .header("X-User-Id", "2")
                        .header("X-User-Name", "bob")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin permission required"));
    }

    @Test
    void getUserByIdUnexpectedExceptionReturnsGenericMessageAndLogsStacktrace(CapturedOutput output) throws Exception {
        when(authService.getEnabledUserById(1L)).thenThrow(new RuntimeException("Data too long for column 'login_ip'"));

        mockMvc.perform(get("/internal/users/1")
                        .header("X-Request-Id", "req-internal-500")
                        .header("X-User-Id", "1")
                        .header("X-User-Name", "alice")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));

        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("iam internal unexpected exception requestId=req-internal-500 method=GET path=/internal/users/1")
                .contains("Data too long for column 'login_ip'");
    }

    @Test
    void getUserByIdBusinessExceptionKeepsBusinessMessage() throws Exception {
        when(authService.getEnabledUserById(1L)).thenThrow(new BusinessException(404, "User not found"));

        mockMvc.perform(get("/internal/users/1")
                        .header("X-User-Id", "1")
                        .header("X-User-Name", "alice")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
