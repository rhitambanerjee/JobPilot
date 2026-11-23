package com.rhitam.jobtracker.auth;

import com.rhitam.jobtracker.common.exception.EmailNotVerifiedException;
import com.rhitam.jobtracker.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Register, verify email, log in/out, reset password. The session cookie (JSESSIONID, HttpOnly) is the login. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,   // 72 = BCrypt's limit
            @Size(max = 100) String displayName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record UserResponse(UUID id, String email, String displayName) {
        static UserResponse from(AuthenticatedUser u) {
            return new UserResponse(u.id(), u.email(), u.displayName());
        }
    }

    /** verificationRequired = true means NO session was started: the user must open the emailed link first. */
    public record RegisterResponse(UUID id, String email, String displayName, boolean verificationRequired) {
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AccountService accounts;
    private final LoginThrottle throttle;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          AccountService accounts, LoginThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.accounts = accounts;
        this.throttle = throttle;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request,
                                     HttpServletRequest http, HttpServletResponse response) {
        AccountService.Registration registration =
                accounts.register(request.email(), request.password(), request.displayName());
        User user = registration.user();
        if (!registration.verificationRequired()) {
            startSession(new AuthenticatedUser(user.getId(), user.getEmail(), "", user.getDisplayName(),
                    user.isEmailVerified(), user.getSessionVersion()), http, response);
        }
        return new RegisterResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                registration.verificationRequired());
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest http, HttpServletResponse response) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        throttle.checkAllowed(email);
        Authentication result;
        try {
            result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException e) {
            throttle.recordFailure(email);
            // same message for "no such user" and "wrong password" so the form can't be used to find accounts
            throw new UnauthorizedException("Invalid email or password");
        }
        throttle.reset(email);
        AuthenticatedUser authenticated = (AuthenticatedUser) result.getPrincipal();
        // checked only AFTER the password is right, so this can't be used to probe which emails are registered
        if (accounts.verificationRequired() && !authenticated.emailVerified()) {
            throw new EmailNotVerifiedException(
                    "Please verify your email first. Open the link we sent you, or request a new one.");
        }
        AuthenticatedUser principal = authenticated.withoutPassword();
        startSession(principal, http, response);
        return UserResponse.from(principal);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        accounts.verifyEmail(request.token());
    }

    /** 202 whether or not the address has an account. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        accounts.resendVerification(request.email());
    }

    /** 202 whether or not the address has an account. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody EmailRequest request) {
        accounts.requestPasswordReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accounts.resetPassword(request.token(), request.newPassword());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void startSession(AuthenticatedUser principal, HttpServletRequest http, HttpServletResponse response) {
        if (http.getSession(false) != null) {
            http.changeSessionId();   // new session id after login: defeats session fixation
        }
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, http, response);
    }
}
