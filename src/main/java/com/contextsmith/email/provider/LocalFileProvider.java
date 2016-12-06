package com.contextsmith.email.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.FileUtils;

public class LocalFileProvider {

  public static final String EMAIL_ROOT_PATH =
      "/Users/rcwang/data/enron_mail_20150507/maildir";

  public static List<MimeMessage> fetchMimeMessages(String userId,
                                                    long maxMessages) {
    List<MimeMessage> messages = null;
    try {
      List<File> emails = findLocalFiles(userId, maxMessages);
      if (emails != null) messages = toMimeMessages(emails);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return messages;
  }

  private static List<MimeMessage> toMimeMessages(List<File> files) throws IOException {
    List<MimeMessage> mimeMessages = new ArrayList<>();
    Properties props = new Properties();
    Session session = Session.getDefaultInstance(props, null);

    for (File file : files) {
      try {
        MimeMessage message = new MimeMessage(session, new FileInputStream(file));
        mimeMessages.add(message);
      } catch (MessagingException e) {
        e.printStackTrace();
        continue;
      }
    }
    return mimeMessages;
  }

  private static List<File> findLocalFiles(String userId, long maxMessages)
      throws IOException {
    File userPath = new File(EMAIL_ROOT_PATH, userId);
    if (!userPath.exists()) return null;

    List<File> emailFiles = new ArrayList<>();
    Iterator<File> iter = FileUtils.iterateFiles(userPath, null, true);

    while (iter.hasNext()) {
      if (maxMessages != -1 && emailFiles.size() >= maxMessages) {
        break;
      }
      emailFiles.add(iter.next());
    }
    return emailFiles;
  }
}
