package com.contextsmith.api.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessRequestCallable implements Callable<String> {
  private static final Logger log = LoggerFactory.getLogger(
      ProcessRequestCallable.class);

  public static final String CALLBACK_REQUEST_METHOD = "POST";
  public static final String CALLBACK_CONTENT_TYPE_HEADER = "Content-Type";
  public static final String CALLBACK_CONTENT_TYPE_VALUE = "application/json; charset=UTF-8";

  public static final String START_DATE_PARAM = "startDate";
  public static final String END_DATE_PARAM = "endDate";

  private static boolean execCallback(String jsonOutput,
                                      String callbackUrl,
                                      Long startTimeInSec,
                                      Long endTimeInSec) {
    if (StringUtils.isBlank(callbackUrl)) return false;
    URL url = null;
    try {
      URIBuilder builder = new URIBuilder(callbackUrl);
      if (startTimeInSec != null && endTimeInSec != null) {
        builder.addParameter(START_DATE_PARAM, Long.toString(startTimeInSec))
               .addParameter(END_DATE_PARAM, Long.toString(endTimeInSec));
      }
      url = builder.build().toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      log.error(String.format("%s (%s)", e, callbackUrl));
    }
    if (url == null) return false;
    try {
      if (ProcessRequestCallable.sendHttpPost(url, jsonOutput) == HttpStatus.OK_200) {
        return true;
      }
    } catch (IOException e) {
      log.error("Unable to process request", e);

      e.printStackTrace();
    }
    return false;
  }

  private static int sendHttpPost(URL url, String json) throws IOException {
    log.debug("Posting json ({} bytes) to URL: {}",
              json.getBytes().length, url);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod(CALLBACK_REQUEST_METHOD);
    conn.setRequestProperty(CALLBACK_CONTENT_TYPE_HEADER,
                            CALLBACK_CONTENT_TYPE_VALUE);
    conn.setDoOutput(true);

    OutputStream os = conn.getOutputStream();
    os.write(json.getBytes(StandardCharsets.UTF_8));
    os.close();

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpStatus.OK_200) {
      log.info("POST successful! Response: {}, {}", responseCode,
               conn.getResponseMessage());
    } else {
      log.error("POST failed! Response: {}, {}", responseCode,
                conn.getResponseMessage());
    }
    return conn.getResponseCode();
  }

  private NewsFeederRequest request;
  private String parentThreadName;

  public ProcessRequestCallable(NewsFeederRequest request,
                                String parentThreadName) {
    this.request = request;
    this.parentThreadName = parentThreadName;
  }

  @Override
  public String call() throws Exception {
    Thread.currentThread().setName(this.parentThreadName);
    String jsonOutput = NewsFeeder.processRequest(this.request);
    execCallback(jsonOutput, this.request.getCallbackUrl(),
                 this.request.getStartTimeInSec(), this.request.getEndTimeInSec());
    return jsonOutput;
  }
}
