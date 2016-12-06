package com.contextsmith.email.provider;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;

public class GoogleServiceProvider {
  private static final Logger log = LoggerFactory.getLogger(GoogleServiceProvider.class);

  public static final String CLIENT_SECRET_FILE = "client_secret.json";
  public static final String APPLICATION_NAME = "Context-Smith Gmail Service Provider";

  // Directory to store user credentials for this application.
  public static final String DEFAULT_DATA_STORE_DIR = "indifferenzetester@gmail.com";
//  public static final String DEFAULT_DATA_STORE_DIR = "rcwang@gmail.com";
  public static final File DEFAULT_DATA_STORE_FILE = new File(
      System.getProperty("user.home"), ".credentials/" + DEFAULT_DATA_STORE_DIR);
  public static final String DEFAULT_ACCESS_TOKEN = "test";

  /** Global instance of the scopes required by this quickstart.
  *
  * If modifying these scopes, delete your previously saved credentials
  * at ~/.credentials/calendar-java-quickstart.json
  */
  private static final List<String> SCOPES = Arrays.asList(
      GmailScopes.GMAIL_READONLY,
      CalendarScopes.CALENDAR_READONLY
  );

  private static HttpTransport HTTP_TRANSPORT;
  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      log.error(e.toString());
      e.printStackTrace();
    }
  }

  public static Credential authorizeAccessToken(String accessToken) {
    checkNotNull(accessToken);

    GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
    tokenResponse.setAccessToken(accessToken);
    return new GoogleCredential().setFromTokenResponse(tokenResponse);
  }

  /**
   * Creates an authorized Credential object.
   * @return an authorized Credential object.
   * @throws IOException
   */
  public static Credential authorizeStoredCredential(File storedCredential)
      throws IOException {
    checkNotNull(storedCredential);

    // Load client secrets.
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    InputStream in = classLoader.getResourceAsStream(CLIENT_SECRET_FILE);
    if (in == null) throw new FileNotFoundException(
        "Could not find: " + CLIENT_SECRET_FILE);
    GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(),
                                 new InputStreamReader(in));

    FileDataStoreFactory dataStoreFactory =
        new FileDataStoreFactory(storedCredential);

    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow
        .Builder(HTTP_TRANSPORT, JacksonFactory.getDefaultInstance(),
                 clientSecrets, SCOPES)
        .setDataStoreFactory(dataStoreFactory)
        .setAccessType("offline")
        .build();

    Credential credential = new AuthorizationCodeInstalledApp(
        flow, new LocalServerReceiver()).authorize("user");
    log.info("Credentials saved to: {}",
             dataStoreFactory.getDataDirectory().getAbsolutePath());
    return credential;
  }

  private Gmail gmailService;
  private Calendar calendarService;
  private String accessToken;
  private File storedCredential;

  /*public GoogleServiceProvider(File storedCredential) {
    this.gmailService = null;
    this.calendarService = null;

    this.accessToken = null;
    this.storedCredential = storedCredential;
  }*/

  public GoogleServiceProvider(String accessToken) {
    checkNotNull(accessToken);
    this.gmailService = null;
    this.calendarService = null;

    if (accessToken.equals(DEFAULT_ACCESS_TOKEN)) {
      this.accessToken = null;
      this.storedCredential = DEFAULT_DATA_STORE_FILE;
    } else {
      this.accessToken = accessToken;
      this.storedCredential = null;
    }
  }

  public Credential authorize() {
    Credential credential = null;
    if (!StringUtils.isBlank(this.accessToken)) {
      credential = authorizeAccessToken(this.accessToken);
    } else if (this.storedCredential != null) {
      try {
        credential = authorizeStoredCredential(this.storedCredential);
      } catch (IOException e) {
        log.error(e.toString());
        e.printStackTrace();
      }
    }
    if (credential == null) return null;
    return credential;
  }

  public String getAccessToken() {
    return this.accessToken;
  }

  /**
   * Build and return an authorized Calendar client service.
   * @return an authorized Calendar client service
   * @throws IOException
   */
  public Calendar getCalendarService() {
    if (this.calendarService != null) return this.calendarService;
      return new Calendar
          .Builder(HTTP_TRANSPORT, JacksonFactory.getDefaultInstance(), authorize())
          .setApplicationName(APPLICATION_NAME)
          .build();
  }

  /**
   * Build and return an authorized Gmail client service.
   * @return an authorized Gmail client service
   * @throws IOException
   */
  public Gmail getGmailService() {
    if (this.gmailService != null) return this.gmailService;
    return new Gmail
        .Builder(HTTP_TRANSPORT, JacksonFactory.getDefaultInstance(), authorize())
        .setApplicationName(APPLICATION_NAME)
        .build();
  }

  public File getStoredCredential() {
    return this.storedCredential;
  }
}