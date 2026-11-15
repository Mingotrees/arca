package com.popman.arca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PasswordRecoveryDisabledTest {
    @Autowired MockMvc mvc;

    @Test
    void unconfiguredRecoveryRoutesFailExplicitlyWithoutBreakingExistingAuth() throws Exception {
        mvc.perform(post("/api/v1/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
        mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "a".repeat(43) + "\",\"new_password\":\"NewPassword1!\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
    }
}
