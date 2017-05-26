package com.contextsmith.api.data;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.email.cluster.EmailNameResolver;
import com.contextsmith.utils.EventUtil;
import com.google.api.services.calendar.model.Event;

public class EventMessage extends AbstractMessage {
  static final Logger log = LoggerFactory.getLogger(EventMessage.class);

  private ZonedDateTime createdTime;
  private ZonedDateTime updatedTime;
  private ZonedDateTime startTime;
  private ZonedDateTime endTime;
  private String location;

  public EventMessage() {
    super();
    this.createdTime = null;
    this.updatedTime = null;
    this.startTime = null;
    this.endTime = null;
    this.location = null;
  }

  public ZonedDateTime getCreatedTime() {
    return this.createdTime;
  }

  @Override
  public Date getDateForTimeline() {
    return Date.from(this.startTime.toInstant());
  }

  public ZonedDateTime getEndTime() {
    return this.endTime;
  }

  public String getLocation() {
    return this.location;
  }

  public ZonedDateTime getStartTime() {
    return this.startTime;
  }

  public ZonedDateTime getUpdatedTime() {
    return this.updatedTime;
  }

  public EventMessage loadFrom(Event event, EmailNameResolver enResolver)
      throws MessagingException {
    this.messageId = event.getId();  // For equals().
    if (this.messageId == null) {
      throw new MessagingException();
    }

    this.startTime = EventUtil.getStartTime(event);
    this.endTime = EventUtil.getEndTime(event);
    if (this.startTime == null || this.endTime == null) {
      return null;
    }

    this.sourceInboxes = EventUtil.getSourceInboxes(event);
    this.createdTime = EventUtil.getCreatedTime(event);
    this.updatedTime = EventUtil.getUpdatedTime(event);

    Set<InternetAddress> organizers = EventUtil.getOrganizer(event);
    if (organizers != null && !organizers.isEmpty()) {
      this.from = enResolver.resolve(organizers);
    }
    Set<InternetAddress> attendees = EventUtil.getAttendees(event);
    if (attendees != null && !attendees.isEmpty()) {
      this.to = enResolver.resolve(attendees);
    }
    if (StringUtils.isNotBlank(event.getSummary())) {
      this.subject = event.getSummary();
    }
    if (StringUtils.isNotBlank(event.getLocation())) {
      this.location = event.getLocation();
    }
    if (StringUtils.isNotBlank(event.getDescription())) {
      this.plainText = event.getDescription();
    }
    return this;
  }
}
