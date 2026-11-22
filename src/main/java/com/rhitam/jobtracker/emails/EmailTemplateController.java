package com.rhitam.jobtracker.emails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email-templates")
public class EmailTemplateController {

    public record TemplateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 300) String subject,
            @NotBlank @Size(max = 10000) String body) {
    }

    /** placeholders = the ones the USER has to fill in (built-in ones are filled automatically). */
    public record TemplateResponse(UUID id, String name, String subject, String body, boolean builtIn,
                                   List<String> placeholders) {
        static TemplateResponse from(EmailTemplate t) {
            return new TemplateResponse(t.getId(), t.getName(), t.getSubject(), t.getBody(), t.isBuiltIn(),
                    TemplateRenderer.customPlaceholders(t.getSubject(), t.getBody()));
        }
    }

    private final EmailTemplateService service;

    public EmailTemplateController(EmailTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemplateResponse> list() {
        return service.list().stream().map(TemplateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody TemplateRequest request) {
        return TemplateResponse.from(service.create(request.name(), request.subject(), request.body()));
    }

    @PutMapping("/{id}")
    public TemplateResponse update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return TemplateResponse.from(service.update(id, request.name(), request.subject(), request.body()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
