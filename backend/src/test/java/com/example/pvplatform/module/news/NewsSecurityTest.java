package com.example.pvplatform.module.news;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {"security.debug-open=false", "news.sync.enabled=false"})
@AutoConfigureMockMvc
class NewsSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousCanReadPublishedNewsList() throws Exception {
        mvc.perform(get("/api/news")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void anonymousCannotReadNotificationsOrAdminNews() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/news")).andExpect(status().isUnauthorized());
    }
}
