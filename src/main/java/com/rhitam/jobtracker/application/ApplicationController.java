package com.rhitam.jobtracker.application;

import com.rhitam.jobtracker.application.dto.ApplicationResponse;
import com.rhitam.jobtracker.application.dto.ChangeStageRequest;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.application.dto.UpdateApplicationRequest;
import com.rhitam.jobtracker.application.dto.UpdateContactEmailRequest;
import com.rhitam.jobtracker.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody CreateApplicationRequest request) {
        return ApplicationResponse.from(service.create(user.id(), request));
    }

    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestParam(required = false) Stage stage) {
        return service.list(user.id(), stage).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApplicationResponse.from(service.get(user.id(), id));
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return ApplicationResponse.from(service.updateDetails(user.id(), id, request));
    }

    @PutMapping("/{id}/contact-email")
    public ApplicationResponse updateContactEmail(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable UUID id,
                                                  @Valid @RequestBody UpdateContactEmailRequest request) {
        return ApplicationResponse.from(service.updateContactEmail(user.id(), id, request.contactEmail()));
    }

    @PostMapping("/{id}/stage")
    public ApplicationResponse changeStage(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID id, @Valid @RequestBody ChangeStageRequest request) {
        return ApplicationResponse.from(service.changeStage(user.id(), id, request.stage(), ApplicationService.ACTOR_USER));
    }
}
