package com.reusehub.rental;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.rental.dto.RentalCreateRequest;
import com.reusehub.rental.dto.RentalDisputeRequest;
import com.reusehub.rental.dto.RentalPageResponse;
import com.reusehub.rental.dto.RentalResponse;
import com.reusehub.rental.dto.RentalStatusUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rentals")
public class RentalController {
  private final RentalService rentalService;

  public RentalController(RentalService rentalService) {
    this.rentalService = rentalService;
  }

  @GetMapping
  public RentalPageResponse list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return rentalService.listForUser(principal, page, size);
  }

  @PostMapping
  public RentalResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody RentalCreateRequest request
  ) {
    return rentalService.create(principal, request);
  }

  @GetMapping("/{id}")
  public RentalResponse get(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id
  ) {
    return rentalService.get(principal, id);
  }

  @PatchMapping("/{id}/status")
  public RentalResponse updateStatus(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody RentalStatusUpdateRequest request
  ) {
    return rentalService.updateStatus(principal, id, request);
  }

  @PostMapping("/{id}/handover")
  public RentalResponse confirmHandover(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id
  ) {
    return rentalService.confirmHandover(principal, id);
  }

  @PostMapping("/{id}/return")
  public RentalResponse requestReturn(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id
  ) {
    return rentalService.requestReturn(principal, id);
  }

  @PostMapping("/{id}/dispute")
  public RentalResponse raiseDispute(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody RentalDisputeRequest request
  ) {
    return rentalService.raiseDispute(principal, id, request.reason());
  }
}
