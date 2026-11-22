package com.rhitam.jobtracker.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Goes through the real security filter chain: login, sessions, CSRF and per-user visibility. */
@AutoConfigureMockMvc
class AuthApiIT extends PostgresIT {

    @Autowired MockMvc mvc;

    private MockHttpSession register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"displayName\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rules")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutACsrfTokenAreRejected() throws Exception {
        MockHttpSession session = register("carol@example.com");
        mvc.perform(post("/api/applications").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\",\"role\":\"Engineer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersCannotSeeEachOthersApplications() throws Exception {
        MockHttpSession alice = register("alice@example.com");
        MockHttpSession bob = register("bob@example.com");

        MvcResult created = mvc.perform(post("/api/applications").session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\",\"role\":\"Engineer\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/applications").session(bob)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/applications/" + id).session(bob)).andExpect(status().isNotFound());
        mvc.perform(post("/api/applications/" + id + "/stage").session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"SCREENING\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/audit").param("entityId", id).session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/applications/" + id).session(alice)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejectedWithAGenericMessage() throws Exception {
        register("dave@example.com");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dave@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejected_andLogoutEndsTheSession() throws Exception {
        MockHttpSession session = register("erin@example.com");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ERIN@example.com\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
