package com.contextsmith.demo;

import java.util.List;

import javax.mail.internet.MimeMessage;

public interface EmailProvider {
  public List<MimeMessage> provide(String userId, String query, 
                                   long maxMessagesToFetch);
}
