package com.reusehub.order;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.order.dto.OrderCreateRequest;
import com.reusehub.order.dto.OrderBundleCreateRequest;
import com.reusehub.order.dto.HandoffConfirmationRequest;
import com.reusehub.order.dto.OrderResponse;
import com.reusehub.order.dto.OrderStatusUpdateRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping
  public List<OrderResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
    return orderService.listForUser(principal.getUserId());
  }

  @PostMapping
  public OrderResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody OrderCreateRequest request
  ) {
    return orderService.create(principal.getUserId(), request);
  }

  @PostMapping("/bundle")
  public List<OrderResponse> createBundle(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody OrderBundleCreateRequest request
  ) {
    return orderService.createBundle(principal.getUserId(), request);
  }

  @PatchMapping("/{id}/status")
  public OrderResponse updateStatus(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody OrderStatusUpdateRequest request
  ) {
    return orderService.updateStatus(principal.getUserId(), id, request);
  }

  @PostMapping("/{id}/handoff-confirmation")
  public OrderResponse confirmHandoff(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody HandoffConfirmationRequest request
  ) {
    return orderService.confirmHandoff(principal.getUserId(), id, request);
  }
}
