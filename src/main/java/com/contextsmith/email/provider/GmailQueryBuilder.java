package com.contextsmith.email.provider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;

public class GmailQueryBuilder {

  private StringBuffer queryBuffer;

  public GmailQueryBuilder() {
    this.queryBuffer = new StringBuffer();
  }

  public GmailQueryBuilder addAfterDate(Date afterThisDate) {
    if (afterThisDate == null) return this;
    SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");
    addQuery("after:" + df.format(afterThisDate));
    return this;
  }

  public GmailQueryBuilder addBeforeDate(Date beforeThisDate) {
    if (beforeThisDate == null) return this;
    SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");
    addQuery("before:" + df.format(beforeThisDate));
    return this;
  }

  public GmailQueryBuilder addClusters(List<Set<InternetAddress>> clusters) {
    if (clusters == null) return this;
    StringBuilder builder = new StringBuilder();

    for (Set<InternetAddress> cluster : clusters) {
      for (InternetAddress address : cluster) {
        if (StringUtils.isBlank(address.getAddress())) continue;
        if (builder.length() > 0) builder.append(" OR ");
        builder.append(String.format("from:%s OR to:%s",
                       address.getAddress(), address.getAddress()));
      }
    }
    addQuery(String.format("(%s)", builder.toString()));
    return this;
  }

  public GmailQueryBuilder addQuery(String s) {
    if (StringUtils.isBlank(s)) return this;
    if (this.queryBuffer.length() > 0) this.queryBuffer.append(" ");
    this.queryBuffer.append(s);
    return this;
  }

  // Note: this will reset the query buffer.
  public String build() {
    String query = this.queryBuffer.toString();
    this.queryBuffer.setLength(0);
    return query;
  }

}
