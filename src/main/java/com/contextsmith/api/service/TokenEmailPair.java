package com.contextsmith.api.service;

import javax.mail.internet.InternetAddress;

import com.contextsmith.utils.InternetAddressUtil;
import com.contextsmith.utils.StringUtil;

public class TokenEmailPair {

  private String token;
  private String email;

  public TokenEmailPair(String accessToken, String emailStr) {
    this.token = accessToken;
    this.email = emailStr;
  }

  public String getAccessToken() {
    return this.token;
  }

  public InternetAddress getEmailAddress() {
    return InternetAddressUtil.newIAddress(this.email);
  }

  public String getEmailStr() {
    return this.email;
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", this.email,
                         StringUtil.substringFromLast(this.token, 5));
  }
}