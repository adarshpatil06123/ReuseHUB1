package com.reusehub.user;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.user.dto.UserResponse;
import com.reusehub.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
    return UserResponse.from(userService.getCurrentUser(principal));
  }

  @PatchMapping("/me")
  public UserResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UserUpdateRequest request
  ) {
    return UserResponse.from(userService.updateProfile(principal, request));
  }

  @GetMapping("/{id}")
  public UserResponse byId(@PathVariable Long id) {
    return UserResponse.from(userService.getById(id));
  }
}
