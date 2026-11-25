package com.ecoportal.controller;

import com.ecoportal.domain.UserRole;
import com.ecoportal.dto.ApplicationResponse;
import com.ecoportal.dto.MyApplicationResponse;
import com.ecoportal.dto.SubmitApplicationRequest;
import com.ecoportal.exception.ForbiddenException;
import com.ecoportal.security.AuthenticatedUser;
import com.ecoportal.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/api/events/{id}/applications")
    public ResponseEntity<ApplicationResponse> submit(
            @PathVariable("id") UUID eventId,
            @Valid @RequestBody(required = false) SubmitApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        requireParticipant(principal);
        String comment = request == null ? null : request.comment();
        ApplicationService.SubmissionResult result =
                applicationService.submitOrRestoreApplication(principal.id(), eventId, comment);
        HttpStatus status = result.restored() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.application());
    }

    @GetMapping("/api/my/applications")
    public List<MyApplicationResponse> myApplications(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireParticipant(principal);
        return applicationService.getMyApplications(principal.id());
    }

    @PostMapping("/api/applications/{id}/cancel")
    public ApplicationResponse cancel(@PathVariable("id") UUID applicationId,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        requireParticipant(principal);
        return applicationService.cancelApplication(principal.id(), applicationId);
    }

    private void requireParticipant(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.PARTICIPANT) {
            throw new ForbiddenException("Ця дія доступна лише учасникам");
        }
    }
}
