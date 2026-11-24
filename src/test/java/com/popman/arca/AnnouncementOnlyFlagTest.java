package com.popman.arca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.homepage.announcements-enabled=true", "app.homepage.trending-enabled=false"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AnnouncementOnlyFlagTest {
    @Autowired MockMvc mvc;
    @Test void announcementsCanBeEnabledWithoutTrending() throws Exception {
        mvc.perform(get("/api/v1/announcements/active").with(user(HomepageIntegrationTest.actor(1)))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/posts/trending").with(user(HomepageIntegrationTest.actor(1)))).andExpect(status().isNotFound());
    }
}
