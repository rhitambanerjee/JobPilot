package com.rhitam.jobtracker.application;

import com.rhitam.jobtracker.application.dto.ApplicationResponse;
import com.rhitam.jobtracker.application.dto.ChangeStageRequest;
import com.rhitam.jobtracker.application.dto.CreateApplicationRequest;
import com.rhitam.jobtracker.application.dto.UpdateContactEmailRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
    public ApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request) {
        return ApplicationResponse.from(service.create(request));
    }

    @GetMapping
    public List<ApplicationResponse> list(@RequestParam(required = false) Stage stage) {
        return service.list(stage).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return ApplicationResponse.from(service.get(id));
    }

    @PutMapping("/{id}/contact-email")
    public ApplicationResponse updateContactEmail(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateContactEmailRequest request) {
        return ApplicationResponse.from(service.updateContactEmail(id, request.contactEmail()));
    }

    @PostMapping("/{id}/stage")
    public ApplicationResponse changeStage(@PathVariable UUID id, @Valid @RequestBody ChangeStageRequest request) {
        return ApplicationResponse.from(service.changeStage(id, request.stage(), ApplicationService.ACTOR_USER));
    }
}
