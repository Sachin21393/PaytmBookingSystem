package com.paytm.project.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://id.example.test/.well-known/jwks.json"
})
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    void annotatedEndpointsRejectMissingBearerTokens() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void annotatedEndpointsUseTheTokenSubjectAsIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").with(jwt().jwt(jwt -> jwt.subject("user-42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"));
    }
}
