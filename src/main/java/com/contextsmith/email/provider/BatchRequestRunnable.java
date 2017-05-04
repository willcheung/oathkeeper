package com.contextsmith.email.provider;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.googleapis.batch.BatchRequest;

public class BatchRequestRunnable implements Runnable {
  static final Logger log = LogManager.getLogger(BatchRequestRunnable.class);

  private BatchRequest batchRequest;
  private boolean isAvailable;  // should probably be volatile - JB

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
      log.error(e);
    } finally {
      this.isAvailable = true;
    }
  }
}