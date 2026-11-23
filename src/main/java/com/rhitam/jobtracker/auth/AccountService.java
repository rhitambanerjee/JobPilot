package com.rhitam.jobtracker.auth;

import com.rhitam.jobtracker.common.exception.BadRequestException;
import com.rhitam.jobtracker.emails.OutgoingEmail;
import com.rhitam.jobtracker.emails.OutgoingEmailRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification and password reset.
 *
 * Design points:
 *  - A new account has NO session until its email is verified, so typing someone else's address gets you nothing.
 *  - Emails are queued in the same transaction (outgoing_emails, kind ACCOUNT) and delivered by OutgoingEmailSender
 *    with the same retries as every other email. A slow or broken mail server never blocks the request.
 *  - "Forgot password" and "resend" always answer the same way, whether or not the address has an account,
 *    so the forms can't be used to find out who is registered. They are also rate limited per address.
 *  - A successful reset also proves mailbox ownership (marks the account verified) and logs out every session.
 */
@Service
public class AccountService {

    private static final String INVALID_LINK = "This link is invalid or has expired. Request a new one.";

    public record Registration(User user, boolean verificationRequired) {
    }

    private final UserService userService;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthTokenService tokens;
    private final RequestLimiter limiter;
    private final OutgoingEmailRepository outgoing;
    private final Clock clock;
    private final boolean requireVerification;
    private final String baseUrl;
    private final Duration verificationTtl;
    private final Duration resetTtl;

    public AccountService(UserService userService, UserRepository users, PasswordEncoder encoder,
                          AuthTokenService tokens, RequestLimiter limiter, OutgoingEmailRepository outgoing,
                          Clock clock,
                          @Value("${tracker.auth.require-email-verification:true}") boolean requireVerification,
                          @Value("${tracker.app.base-url:http://localhost:5173}") String baseUrl,
                          @Value("${tracker.auth.verification-ttl-hours:24}") long verificationHours,
                          @Value("${tracker.auth.reset-ttl-minutes:60}") long resetMinutes) {
        this.userService = userService;
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limiter = limiter;
        this.outgoing = outgoing;
        this.clock = clock;
        this.requireVerification = requireVerification;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.verificationTtl = Duration.ofHours(verificationHours);
        this.resetTtl = Duration.ofMinutes(resetMinutes);
    }

    public boolean verificationRequired() {
        return requireVerification;
    }

    @Transactional
    public Registration register(String email, String password, String displayName) {
        User user = userService.create(email, password, displayName);
        if (!requireVerification) {
            userService.claimExistingData(user);
            return new Registration(user, false);
        }
        queueVerificationEmail(user);
        return new Registration(user, true);
    }

    /** Always returns normally: the caller can't tell whether the address has an account. */
    @Transactional
    public void resendVerification(String email) {
        users.findByEmailIgnoreCase(normalize(email))
                .filter(user -> !user.isEmailVerified())
                .filter(user -> limiter.tryAcquire("verify:" + user.getEmail()))
                .ifPresent(this::queueVerificationEmail);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = tokens.consume(rawToken, AuthTokenService.Type.VERIFY_EMAIL)
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));
        userService.markVerified(userId);
    }

    /** Always returns normally: the caller can't tell whether the address has an account. */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(normalize(email))
                .filter(user -> !UserService.LEGACY_USER_ID.equals(user.getId()))
                .filter(user -> limiter.tryAcquire("reset:" + user.getEmail()))
                .ifPresent(this::queueResetEmail);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = tokens.consume(rawToken, AuthTokenService.Type.RESET_PASSWORD)
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));
        User user = users.findById(userId).orElseThrow(() -> new BadRequestException(INVALID_LINK));
        user.changePassword(encoder.encode(newPassword));                 // also ends every existing session
        userService.markVerified(userId);                                  // they read our email, so the mailbox is theirs
        tokens.invalidateAll(userId, AuthTokenService.Type.RESET_PASSWORD);
    }

    private void queueVerificationEmail(User user) {
        String token = tokens.issue(user.getId(), AuthTokenService.Type.VERIFY_EMAIL, verificationTtl);
        String body = "Hi" + nameOf(user) + ",\n\n"
                + "Confirm your email address to finish creating your Job Tracker account:\n\n"
                + baseUrl + "/#verify=" + token + "\n\n"
                + "This link expires in " + verificationTtl.toHours() + " hours. "
                + "If you didn't create an account, you can ignore this email.\n";
        queue(user, "Confirm your email for Job Tracker", body);
    }

    private void queueResetEmail(User user) {
        String token = tokens.issue(user.getId(), AuthTokenService.Type.RESET_PASSWORD, resetTtl);
        String body = "Hi" + nameOf(user) + ",\n\n"
                + "We got a request to reset your Job Tracker password. Choose a new one here:\n\n"
                + baseUrl + "/#reset=" + token + "\n\n"
                + "This link expires in " + resetTtl.toMinutes() + " minutes and works once. "
                + "If you didn't ask for this, ignore this email: your password stays the same.\n";
        queue(user, "Reset your Job Tracker password", body);
    }

    private void queue(User user, String subject, String body) {
        outgoing.save(OutgoingEmail.account(user.getId(), user.getEmail(), subject, body, clock.instant()));
    }

    private static String nameOf(User user) {
        return user.getDisplayName() == null ? "" : " " + user.getDisplayName();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
