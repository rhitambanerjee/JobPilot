package com.rhitam.jobtracker.analytics;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rhitam.jobtracker.PostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The funnel endpoint and the edit endpoint, through the real security chain. */
@AutoConfigureMockMvc
class AnalyticsApiIT extends PostgresIT {

    @Autowired MockMvc mvc;

    private MockHttpSession register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createApplication(MockHttpSession session) throws Exception {
        MvcResult created = mvc.perform(post("/api/applications").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\",\"role\":\"Engineer\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void funnelReflectsOnlyTheCallersOwnApplications() throws Exception {
        MockHttpSession alice = register("alice@example.com");
        MockHttpSession bob = register("bob@example.com");

        String id = createApplication(alice);
        mvc.perform(post("/api/applications/" + id + "/stage").session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SCREENING\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/analytics/funnel").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.responded").value(1))
                .andExpect(jsonPath("$.funnel[0].stage").value("APPLIED"))
                .andExpect(jsonPath("$.funnel[1].stage").value("SCREENING"))
                .andExpect(jsonPath("$.funnel[1].count").value(1))
                .andExpect(jsonPath("$.weekly.length()").value(12));

        mvc.perform(get("/api/analytics/funnel").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.responseRatePercent").value(nullValue()));
    }

    @Test
    void funnelNeedsALogin() throws Exception {
        mvc.perform(get("/api/analytics/funnel")).andExpect(status().isUnauthorized());
    }

    @Test
    void editEndpointUpdatesDetailsValidatesInputAndRespectsOwnership() throws Exception {
        MockHttpSession alice = register("alice@example.com");
        MockHttpSession bob = register("bob@example.com");
        String id = createApplication(alice);

        mvc.perform(put("/api/applications/" + id).session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme Corp\",\"role\":\"Senior Engineer\",\"jobUrl\":\"https://acme.example\",\"notes\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Acme Corp"))
                .andExpect(jsonPath("$.stage").value("APPLIED"));

        mvc.perform(put("/api/applications/" + id).session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"role\":\"Engineer\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/applications/" + id).session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\",\"role\":\"Engineer\",\"jobUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/applications/" + id).session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Hacked\",\"role\":\"Hacked\"}"))
                .andExpect(status().isNotFound());
    }
}
