package com.contextsmith.demo;

import java.io.IOException;

import com.google.api.client.googleapis.batch.BatchRequest;

public class BatchRequestRunnable implements Runnable {
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
  
  public void run() {
    this.isAvailable = false;
    try {
      this.batchRequest.execute();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      this.isAvailable = true;
    }
  }
}