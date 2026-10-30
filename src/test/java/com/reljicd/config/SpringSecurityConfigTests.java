package com.reljicd.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "spring.admin.password=test-admin-pass",
        "spring.h2.console.enabled=true"
})
@AutoConfigureMockMvc
public class SpringSecurityConfigTests {

    @Autowired
    private MockMvc mvc;

    @Test
    public void loginPageCarriesCsrfTokenAndSameOriginFraming() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    public void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", "test-admin-pass"))
                .andExpect(redirectedUrl("/403"))
                .andExpect(unauthenticated());
    }

    @Test
    public void adminLogsInWithConfiguredPassword() throws Exception {
        mvc.perform(formLogin().user("admin").password("test-admin-pass"))
                .andExpect(redirectedUrl("/home"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    public void adminCannotLogInWithUsernameAsPassword() throws Exception {
        mvc.perform(formLogin().user("admin").password("admin"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    public void logoutNeedsPostWithCsrfToken() throws Exception {
        mvc.perform(get("/logout").with(user("user")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/logout").with(user("user")))
                .andExpect(redirectedUrl("/403"));
        mvc.perform(post("/logout").with(user("user")).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    public void h2ConsoleIsAdminOnly() throws Exception {
        mvc.perform(get("/h2-console/"))
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/h2-console/").with(user("user").roles("USER")))
                .andExpect(redirectedUrl("/403"));
    }
}
