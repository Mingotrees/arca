package com.popman.arca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.homepage.announcements-enabled=false", "app.homepage.trending-enabled=true"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class TrendingOnlyFlagTest {
    @Autowired MockMvc mvc;
    @Test void trendingCanBeEnabledWithoutAnnouncements() throws Exception {
        mvc.perform(get("/api/v1/posts/trending").with(user(HomepageIntegrationTest.actor(1)))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/announcements/active").with(user(HomepageIntegrationTest.actor(1)))).andExpect(status().isNotFound());
    }
}
