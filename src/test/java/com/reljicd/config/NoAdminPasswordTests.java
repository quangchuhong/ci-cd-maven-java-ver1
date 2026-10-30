package com.reljicd.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

/**
 * Without ADMIN_PASSWORD there is no in-memory admin account at all.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.admin.password=")
@AutoConfigureMockMvc
public class NoAdminPasswordTests {

    @Autowired
    private MockMvc mvc;

    @Test
    public void noAdminAccountWithoutPassword() throws Exception {
        mvc.perform(formLogin().user("admin").password(""))
                .andExpect(unauthenticated());
        mvc.perform(formLogin().user("admin").password("admin"))
                .andExpect(unauthenticated());
    }
}
