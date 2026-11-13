package com.reusehub.dispute;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.dispute.dto.DisputeCreateRequest;
import com.reusehub.dispute.dto.DisputeResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class DisputeController {
  private final DisputeService disputeService;

  public DisputeController(DisputeService disputeService) {
    this.disputeService = disputeService;
  }

  @GetMapping("/disputes")
  public List<DisputeResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
    return disputeService.listForUser(principal.getUserId());
  }

  @PostMapping("/orders/{orderId}/disputes")
  public ResponseEntity<DisputeResponse> open(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID orderId,
      @Valid @RequestBody DisputeCreateRequest request
  ) {
    return ResponseEntity.status(201).body(disputeService.open(principal.getUserId(), orderId, request));
  }

  @PostMapping(value = "/disputes/{disputeId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DisputeResponse uploadEvidence(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID disputeId,
      @RequestParam("file") MultipartFile file
  ) {
    return disputeService.addEvidence(principal.getUserId(), disputeId, file);
  }

  @PostMapping("/disputes/{disputeId}/close")
  public DisputeResponse confirmClose(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID disputeId
  ) {
    return disputeService.confirmClose(principal.getUserId(), disputeId);
  }

  @GetMapping("/disputes/{disputeId}/evidence/{evidenceId}/download")
  public ResponseEntity<byte[]> downloadEvidence(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID disputeId,
      @PathVariable UUID evidenceId
  ) {
    DisputeService.EvidenceDownload file = disputeService.downloadEvidence(
        principal.getUserId(),
        disputeId,
        evidenceId
    );
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(file.contentType()));
    headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
    headers.setCacheControl(CacheControl.noStore());
    headers.set("X-Content-Type-Options", "nosniff");
    return ResponseEntity.ok().headers(headers).body(file.content());
  }
}
