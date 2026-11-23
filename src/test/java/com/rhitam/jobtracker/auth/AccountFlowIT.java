package com.rhitam.jobtracker.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rhitam.jobtracker.PostgresIT;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Email verification and password reset, end to end through the real security chain. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "tracker.auth.require-email-verification=true")
class AccountFlowIT extends PostgresIT {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    // ---- helpers ----

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void register(String email) throws Exception {
        postJson("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    /** Reads the link out of the queued email, exactly as the user would (kind = "verify" or "reset"). */
    private String tokenFor(String email, String kind) {
        String body = jdbc.queryForObject("""
                SELECT body FROM outgoing_emails
                WHERE to_address = ? AND kind = 'ACCOUNT' AND body LIKE ?
                ORDER BY created_at DESC LIMIT 1""", String.class, email, "%#" + kind + "=%");
        Matcher m = Pattern.compile("#" + kind + "=([A-Za-z0-9_-]+)").matcher(body);
        assertTrue(m.find(), "no " + kind + " link in the email");
        return m.group(1);
    }

    private long accountEmails(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM outgoing_emails WHERE to_address = ? AND kind = 'ACCOUNT'",
                Long.class, email);
    }

    private void verify(String email) throws Exception {
        postJson("/api/auth/verify-email", "{\"token\":\"" + tokenFor(email, "verify") + "\"}")
                .andExpect(status().isNoContent());
    }

    // ---- verification ----

    @Test
    void cannotLogInUntilTheEmailIsVerified() throws Exception {
        register("a@example.com");

        login("a@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        verify("a@example.com");
        login("a@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void registeringDoesNotStartASession() throws Exception {
        MvcResult result = postJson("/api/auth/register",
                "{\"email\":\"b@example.com\",\"password\":\"" + PASSWORD + "\"}").andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aWrongPasswordDoesNotRevealWhetherTheAccountIsVerified() throws Exception {
        register("c@example.com");
        login("c@example.com", "wrong-password-1").andExpect(status().isUnauthorized());
    }

    @Test
    void aVerificationLinkWorksOnlyOnce() throws Exception {
        register("d@example.com");
        String token = tokenFor("d@example.com", "verify");

        postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isNoContent());
        postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void expiredAndInventedLinksAreRejected() throws Exception {
        register("e@example.com");
        UUID id = users.findByEmailIgnoreCase("e@example.com").orElseThrow().getId();
        String expired = tokens.issue(id, AuthTokenService.Type.VERIFY_EMAIL, Duration.ofSeconds(-5));

        postJson("/api/auth/verify-email", "{\"token\":\"" + expired + "\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\":\"not-a-real-token\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aResetLinkCannotBeUsedAsAVerificationLink() throws Exception {
        register("f@example.com");
        UUID id = users.findByEmailIgnoreCase("f@example.com").orElseThrow().getId();
        String resetToken = tokens.issue(id, AuthTokenService.Type.RESET_PASSWORD, Duration.ofHours(1));

        postJson("/api/auth/verify-email", "{\"token\":\"" + resetToken + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void resendingIsRateLimited_andNeverRevealsWhoIsRegistered() throws Exception {
        register("g@example.com");                                   // 1 email from registration
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/resend-verification", "{\"email\":\"g@example.com\"}").andExpect(status().isAccepted());
        }
        assertEquals(1 + 3, accountEmails("g@example.com"));          // resends are capped at 3 per hour

        postJson("/api/auth/resend-verification", "{\"email\":\"nobody@example.com\"}").andExpect(status().isAccepted());
        assertEquals(0, accountEmails("nobody@example.com"));         // same answer, but nothing is sent
    }

    @Test
    void onlyTheNewestVerificationLinkWorks() throws Exception {
        register("h@example.com");
        String first = tokenFor("h@example.com", "verify");
        postJson("/api/auth/resend-verification", "{\"email\":\"h@example.com\"}").andExpect(status().isAccepted());
        String second = tokenFor("h@example.com", "verify");

        postJson("/api/auth/verify-email", "{\"token\":\"" + first + "\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\":\"" + second + "\"}").andExpect(status().isNoContent());
    }

    // ---- password reset ----

    @Test
    void resettingThePasswordChangesIt_andEndsEveryOldSession() throws Exception {
        register("i@example.com");
        verify("i@example.com");
        MvcResult loggedIn = login("i@example.com", PASSWORD).andExpect(status().isOk()).andReturn();
        MockHttpSession oldSession = (MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(oldSession)).andExpect(status().isOk());

        postJson("/api/auth/forgot-password", "{\"email\":\"i@example.com\"}").andExpect(status().isAccepted());
        String token = tokenFor("i@example.com", "reset");
        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"brand-new-pass-9\"}")
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(oldSession)).andExpect(status().isUnauthorized());   // old session is dead
        login("i@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("i@example.com", "brand-new-pass-9").andExpect(status().isOk());

        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"another-pass-123\"}")
                .andExpect(status().isBadRequest());                                                  // single use
    }

    @Test
    void forgotPasswordAnswersTheSameForUnknownAddressesAndSendsNothing() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"ghost@example.com\"}").andExpect(status().isAccepted());
        assertEquals(0, accountEmails("ghost@example.com"));
    }

    @Test
    void aSuccessfulResetAlsoVerifiesTheAccount() throws Exception {
        register("j@example.com");                                    // never clicked the verification link
        postJson("/api/auth/forgot-password", "{\"email\":\"j@example.com\"}").andExpect(status().isAccepted());
        String token = tokenFor("j@example.com", "reset");
        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"brand-new-pass-9\"}")
                .andExpect(status().isNoContent());

        login("j@example.com", "brand-new-pass-9").andExpect(status().isOk());
    }

    @Test
    void newPasswordsMustMeetTheLengthRule() throws Exception {
        register("k@example.com");
        postJson("/api/auth/forgot-password", "{\"email\":\"k@example.com\"}");
        String token = tokenFor("k@example.com", "reset");

        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest());
    }
}
