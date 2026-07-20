package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.model.PatientAttachment;
import com.roberthevesi.cryptoshred_health.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/records/{recordId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable UUID recordId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails currentUser) throws IOException {

        AttachmentResponse response = attachmentService.uploadAttachment(recordId, file, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AttachmentResponse>> getAttachments(
            @PathVariable UUID recordId,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity.ok(attachmentService.getAttachmentsForRecord(recordId, currentUser.getUsername()));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable UUID recordId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal UserDetails currentUser) {

        byte[] fileBytes = attachmentService.downloadAttachment(attachmentId, currentUser.getUsername());
        PatientAttachment meta = attachmentService.getAttachmentMetadata(attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getFileName() + "\"")
                .body(fileBytes);
    }
}
