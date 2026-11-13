package com.reusehub.auth;

public interface EmailService {
  void sendEmailVerification(String email, String verificationLink);
}
