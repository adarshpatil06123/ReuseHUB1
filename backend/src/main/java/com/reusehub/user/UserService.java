package com.reusehub.user;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.user.dto.UserUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User getCurrentUser(UserPrincipal principal) {
    return userRepository.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
  }

  public User getById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
  }

  public User updateProfile(UserPrincipal principal, UserUpdateRequest request) {
    User user = getCurrentUser(principal);

    if (request.name() != null && !request.name().isBlank()) {
      user.setName(request.name().trim());
    }
    if (request.username() != null && !request.username().isBlank()) {
      user.setUsername(request.username().trim());
    }
    if (request.profileImageUrl() != null && !request.profileImageUrl().isBlank()) {
      user.setProfileImageUrl(request.profileImageUrl().trim());
    }
    if (request.department() != null) {
      user.setDepartment(request.department().trim());
    }
    if (request.college() != null) {
      user.setCollege(request.college().trim());
    }
    if (request.phoneNumber() != null) {
      user.setPhoneNumber(request.phoneNumber().trim());
    }
    if (request.location() != null) {
      user.setLocation(request.location().trim());
    }

    return userRepository.save(user);
  }
}
