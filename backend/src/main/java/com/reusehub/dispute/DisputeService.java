package com.reusehub.dispute;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.dispute.dto.DisputeCreateRequest;
import com.reusehub.dispute.dto.DisputeEvidenceResponse;
import com.reusehub.dispute.dto.DisputeResponse;
import com.reusehub.order.Order;
import com.reusehub.order.OrderRepository;
import com.reusehub.order.OrderStatus;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DisputeService {
  private static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024;
  private static final String GENESIS_HASH = "0".repeat(64);

  private final DisputeRepository disputeRepository;
  private final DisputeEvidenceRepository evidenceRepository;
  private final OrderRepository orderRepository;
  private final UserRepository userRepository;
  private final Path evidenceDirectory;

  public DisputeService(
      DisputeRepository disputeRepository,
      DisputeEvidenceRepository evidenceRepository,
      OrderRepository orderRepository,
      UserRepository userRepository,
      @Value("${reusehub.disputes.storage-dir:dispute-evidence}") String evidenceDirectory
  ) {
    this.disputeRepository = disputeRepository;
    this.evidenceRepository = evidenceRepository;
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
    this.evidenceDirectory = Path.of(evidenceDirectory).toAbsolutePath().normalize();
  }

  @Transactional
  public List<DisputeResponse> listForUser(Long userId) {
    return disputeRepository
        .findByOrder_Buyer_IdOrOrder_Seller_IdOrderByOpenedAtDesc(userId, userId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public DisputeResponse open(Long userId, UUID orderId, DisputeCreateRequest request) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    ensureParticipant(order, userId);

    if (order.getStatus() != OrderStatus.ACCEPTED
        && order.getStatus() != OrderStatus.COMPLETED
        && order.getStatus() != OrderStatus.NO_SHOW) {
      throw new ApiException(HttpStatus.CONFLICT, "A dispute can only be opened for an accepted, completed, or no-show order");
    }
    if (disputeRepository.findByOrder_Id(orderId).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "A dispute already exists for this order");
    }

    User openedBy = userRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    Dispute dispute = new Dispute();
    dispute.setOrder(order);
    dispute.setOpenedBy(openedBy);
    dispute.setReason(request.reason().trim());
    return toResponse(disputeRepository.save(dispute));
  }

  @Transactional
  public DisputeResponse addEvidence(Long userId, UUID disputeId, MultipartFile file) {
    Dispute dispute = findParticipantDisputeForUpdate(userId, disputeId);
    if (dispute.getStatus() != DisputeStatus.OPEN) {
      throw new ApiException(HttpStatus.CONFLICT, "Evidence cannot be added to a closed dispute");
    }
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a non-empty PNG, JPEG, or PDF file");
    }
    if (file.getSize() > MAX_EVIDENCE_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Evidence files must be 10 MB or smaller");
    }

    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the uploaded evidence file");
    }
    DetectedFile detected = detectFileType(content);
    var previousEvidence = evidenceRepository
        .findTopByDispute_IdOrderBySequenceNumberDesc(disputeId)
        .orElse(null);
    String previousHash = previousEvidence == null ? GENESIS_HASH : previousEvidence.getRecordHash();
    long sequence = previousEvidence == null ? 1 : previousEvidence.getSequenceNumber() + 1;
    String originalFilename = safeFilename(file.getOriginalFilename());
    String contentHash = sha256(content);
    String storageKey = UUID.randomUUID() + detected.extension();

    DisputeEvidence evidence = new DisputeEvidence();
    evidence.setDispute(dispute);
    evidence.setUploadedBy(userRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found")));
    evidence.setSequenceNumber(sequence);
    evidence.setOriginalFilename(originalFilename);
    evidence.setContentType(detected.contentType());
    evidence.setSizeBytes(content.length);
    evidence.setSha256(contentHash);
    evidence.setPreviousRecordHash(previousHash);
    evidence.setStorageKey(storageKey);
    evidence.setUploadedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

    String recordHash = recordHash(evidence);
    evidence.setRecordHash(recordHash);

    try {
      Files.createDirectories(evidenceDirectory);
      Files.write(
          evidencePath(storageKey),
          content,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE
      );
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store evidence");
    }

    try {
      evidenceRepository.save(evidence);
    } catch (RuntimeException exception) {
      try {
        Files.deleteIfExists(evidencePath(storageKey));
      } catch (IOException cleanupException) {
        exception.addSuppressed(cleanupException);
      }
      throw exception;
    }
    return toResponse(dispute);
  }

  @Transactional
  public DisputeResponse confirmClose(Long userId, UUID disputeId) {
    Dispute dispute = findParticipantDisputeForUpdate(userId, disputeId);
    if (dispute.getStatus() == DisputeStatus.CLOSED) {
      return toResponse(dispute);
    }

    Instant now = Instant.now();
    if (dispute.getOrder().getBuyer().getId().equals(userId)) {
      if (dispute.getBuyerCloseConfirmedAt() == null) {
        dispute.setBuyerCloseConfirmedAt(now);
      }
    } else if (dispute.getSellerCloseConfirmedAt() == null) {
      dispute.setSellerCloseConfirmedAt(now);
    }

    if (dispute.getBuyerCloseConfirmedAt() != null && dispute.getSellerCloseConfirmedAt() != null) {
      dispute.setStatus(DisputeStatus.CLOSED);
      dispute.setClosedAt(now);
    }
    return toResponse(disputeRepository.save(dispute));
  }

  @Transactional
  public EvidenceDownload downloadEvidence(Long userId, UUID disputeId, UUID evidenceId) {
    Dispute dispute = findParticipantDisputeForUpdate(userId, disputeId);
    DisputeEvidence evidence = evidenceRepository.findByIdAndDispute_Id(evidenceId, disputeId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Evidence not found"));
    boolean integrityVerified = evidenceResponses(dispute).stream()
        .filter(item -> item.id().equals(evidenceId))
        .findFirst()
        .map(DisputeEvidenceResponse::integrityVerified)
        .orElse(false);
    if (!integrityVerified) {
      throw new ApiException(HttpStatus.CONFLICT, "Evidence integrity verification failed; the file may have been altered");
    }
    try {
      return new EvidenceDownload(
          Files.readAllBytes(evidencePath(evidence.getStorageKey())),
          evidence.getContentType(),
          "evidence-" + evidence.getSequenceNumber() + extensionFor(evidence.getContentType())
      );
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the stored evidence file");
    }
  }

  private Dispute findParticipantDisputeForUpdate(Long userId, UUID disputeId) {
    Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dispute not found"));
    ensureParticipant(dispute.getOrder(), userId);
    return dispute;
  }

  private void ensureParticipant(Order order, Long userId) {
    if (!order.getBuyer().getId().equals(userId) && !order.getSeller().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Only the order participants can access this dispute");
    }
  }

  private DisputeResponse toResponse(Dispute dispute) {
    return new DisputeResponse(
        dispute.getId(),
        dispute.getOrder().getId(),
        dispute.getOrder().getListing().getTitle(),
        dispute.getReason(),
        dispute.getStatus(),
        dispute.getOpenedBy().getName(),
        dispute.getOpenedAt(),
        dispute.getBuyerCloseConfirmedAt(),
        dispute.getSellerCloseConfirmedAt(),
        dispute.getClosedAt(),
        evidenceResponses(dispute)
    );
  }

  private List<DisputeEvidenceResponse> evidenceResponses(Dispute dispute) {
    List<DisputeEvidence> records = evidenceRepository
        .findByDispute_IdOrderBySequenceNumberAsc(dispute.getId());
    List<DisputeEvidenceResponse> responses = new ArrayList<>(records.size());
    String expectedPreviousHash = GENESIS_HASH;
    long expectedSequence = 1;
    for (DisputeEvidence evidence : records) {
      boolean chainVerified = evidence.getSequenceNumber() == expectedSequence
          && expectedPreviousHash.equals(evidence.getPreviousRecordHash())
          && recordHash(evidence).equals(evidence.getRecordHash());
      boolean fileVerified = false;
      try {
        fileVerified = Files.exists(evidencePath(evidence.getStorageKey()))
            && sha256(Files.readAllBytes(evidencePath(evidence.getStorageKey()))).equals(evidence.getSha256());
      } catch (IOException exception) {
        fileVerified = false;
      }
      responses.add(new DisputeEvidenceResponse(
          evidence.getId(),
          evidence.getSequenceNumber(),
          evidence.getOriginalFilename(),
          evidence.getContentType(),
          evidence.getSizeBytes(),
          evidence.getSha256(),
          evidence.getPreviousRecordHash(),
          evidence.getRecordHash(),
          chainVerified && fileVerified,
          evidence.getUploadedBy().getName(),
          evidence.getUploadedAt()
      ));
      expectedPreviousHash = evidence.getRecordHash();
      expectedSequence++;
    }
    return List.copyOf(responses);
  }

  private Path evidencePath(String storageKey) {
    Path path = evidenceDirectory.resolve(storageKey).normalize();
    if (!path.startsWith(evidenceDirectory)) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored evidence location");
    }
    return path;
  }

  private DetectedFile detectFileType(byte[] content) {
    if (content.length >= 8
        && (content[0] & 0xff) == 0x89
        && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47
        && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) {
      return new DetectedFile("image/png", ".png");
    }
    if (content.length >= 3
        && (content[0] & 0xff) == 0xff
        && (content[1] & 0xff) == 0xd8
        && (content[2] & 0xff) == 0xff) {
      return new DetectedFile("image/jpeg", ".jpg");
    }
    if (content.length >= 5
        && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
        && content[3] == 'F' && content[4] == '-') {
      return new DetectedFile("application/pdf", ".pdf");
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "Evidence must be a valid PNG, JPEG, or PDF file");
  }

  private String safeFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return "evidence";
    }
    String basename = filename.replace('\\', '/');
    basename = basename.substring(basename.lastIndexOf('/') + 1)
        .replaceAll("[\\p{Cntrl}]", "")
        .trim();
    if (basename.isBlank()) {
      return "evidence";
    }
    return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
  }

  private String recordHash(DisputeEvidence evidence) {
    String canonical = evidence.getDispute().getId() + "\n"
        + evidence.getSequenceNumber() + "\n"
        + evidence.getUploadedBy().getId() + "\n"
        + evidence.getOriginalFilename() + "\n"
        + evidence.getContentType() + "\n"
        + evidence.getSizeBytes() + "\n"
        + evidence.getSha256() + "\n"
        + evidence.getPreviousRecordHash() + "\n"
        + evidence.getUploadedAt();
    return sha256(canonical.getBytes(StandardCharsets.UTF_8));
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/png" -> ".png";
      case "image/jpeg" -> ".jpg";
      case "application/pdf" -> ".pdf";
      default -> ".bin";
    };
  }

  private record DetectedFile(String contentType, String extension) {}

  public record EvidenceDownload(byte[] content, String contentType, String filename) {}
}
