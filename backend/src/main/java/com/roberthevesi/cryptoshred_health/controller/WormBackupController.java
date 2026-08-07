package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.WormSnapshotDto;
import com.roberthevesi.cryptoshred_health.service.WormBackupExporterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class WormBackupController {

    private final WormBackupExporterService wormBackupExporterService;

    /** Triggers an immediate point-in-time WORM backup snapshot export (returns metadata summary). */
    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('AUDITOR', 'DOCTOR')")
    public ResponseEntity<WormSnapshotDto> exportSnapshot() {
        WormSnapshotDto snapshot = wormBackupExporterService.exportSnapshot();
        snapshot.setRecords(null); // Omit full data payload from export trigger response
        return ResponseEntity.ok(snapshot);
    }

    /** Lists metadata summaries for all exported WORM backup snapshot files. */
    @GetMapping("/snapshots")
    @PreAuthorize("hasAnyRole('AUDITOR', 'DOCTOR')")
    public ResponseEntity<List<WormSnapshotDto>> listSnapshots() {
        List<WormSnapshotDto> snapshots = wormBackupExporterService.listSnapshots();
        return ResponseEntity.ok(snapshots);
    }

    /** Fetches the full WORM backup snapshot payload (including record entries) for a specific snapshot file. */
    @GetMapping("/snapshots/{fileName}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'DOCTOR')")
    public ResponseEntity<WormSnapshotDto> getSnapshotByFileName(@PathVariable String fileName) {
        WormSnapshotDto snapshot = wormBackupExporterService.getSnapshotByFileName(fileName);
        return ResponseEntity.ok(snapshot);
    }


    /** Verifies post-shred zero-purge decryption failure on an immutable WORM snapshot record. */
    @PostMapping("/verify-shred")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<Map<String, String>> verifyPostShredDecryption(
            @RequestParam String fileName,
            @RequestParam UUID recordId) {
        String result = wormBackupExporterService.verifyPostShredDecryptionFailure(fileName, recordId);
        return ResponseEntity.ok(Map.of(
                "fileName", fileName,
                "recordId", recordId.toString(),
                "result", result
        ));
    }
}
