package com.reusehub.need;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.need.dto.NeedRequestCreateRequest;
import com.reusehub.need.dto.NeedRequestResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/needs")
public class NeedRequestController {
  private final NeedRequestService needRequestService;

  public NeedRequestController(NeedRequestService needRequestService) {
    this.needRequestService = needRequestService;
  }

  @GetMapping
  public List<NeedRequestResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
    return needRequestService.listForUser(principal.getUserId());
  }

  @PostMapping
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  public NeedRequestResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody NeedRequestCreateRequest request
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return needRequestService.create(principal.getUserId(), request);
  }

  @PatchMapping("/{id}/publish")
  public NeedRequestResponse publish(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return needRequestService.publish(principal.getUserId(), id);
  }

  @DeleteMapping("/{id}")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    needRequestService.delete(principal.getUserId(), id);
  }
}
