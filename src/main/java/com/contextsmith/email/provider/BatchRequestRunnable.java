package com.contextsmith.email.provider;

import java.io.IOException;

import com.google.api.client.googleapis.batch.BatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchRequestRunnable implements Runnable {
  static final Logger log = LoggerFactory.getLogger(BatchRequestRunnable.class);

  private BatchRequest batchRequest;
  private boolean isAvailable;

  BatchRequestRunnable(BatchRequest batchRequest) {
    this.batchRequest = batchRequest;
    this.isAvailable = true;
  }

  public BatchRequest getBatchRequest() {
    return this.batchRequest;
  }

  public boolean isAvailable() {
    return this.isAvailable;
  }

  @Override
  public void run() {
    this.isAvailable = false;
    try {
      this.batchRequest.execute();
    } catch (IOException e) {
      log.error("Batch Request Failed", e);
    } finally {
      this.isAvailable = true;
    }
  }
}
