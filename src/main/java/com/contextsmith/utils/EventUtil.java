package com.contextsmith.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;

public class EventUtil {
  static final Logger log = LoggerFactory.getLogger(EventUtil.class);

  public static Set<InternetAddress> getAttendees(Event event) {
    Set<InternetAddress> results = new HashSet<>();
    if (event.getAttendees() == null) return results;
    for (EventAttendee attendee : event.getAttendees()) {
      String email = attendee.getEmail();
      if (!EmailValidator.getInstance().isValid(email)) continue;
      results.add(InternetAddressUtil.newIAddress(email, attendee.getDisplayName()));
    }
    return results;
  }

  public static ZonedDateTime getCreatedTime(Event event) {
    return toZonedDateTime(event.getCreated());
  }

  public static ZonedDateTime getEndTime(Event event) {
    return toZonedDateTime(event.getEnd());
  }

  public static Set<InternetAddress> getOrganizer(Event event) {
    Set<InternetAddress> results = new HashSet<>();
    if (event.getOrganizer() == null) return results;
    String email = event.getOrganizer().getEmail();
    if (!EmailValidator.getInstance().isValid(email)) return results;
    String displayName = event.getOrganizer().getDisplayName();
    results.add(InternetAddressUtil.newIAddress(email, displayName));
    return results;
  }

  public static String[] getSourceInboxes(Event event) {
    @SuppressWarnings("unchecked")
    List<String> sources = (List<String>) event.get(
        MimeMessageUtil.SOURCE_INBOX_HEADER);
    if (sources == null) return null;
    return sources.toArray(new String[sources.size()]);
  }

  public static ZonedDateTime getStartTime(Event event) {
    return toZonedDateTime(event.getStart());
  }

  public static ZonedDateTime getUpdatedTime(Event event) {
    return toZonedDateTime(event.getUpdated());
  }

  public static ZonedDateTime toZonedDateTime(DateTime dt) {
    Instant i = Instant.ofEpochMilli(dt.getValue());

    ZoneOffset offset = ZoneOffset.ofTotalSeconds(dt.getTimeZoneShift() * 60);
    // = ZoneOffset.ofHoursMinutes(0, dt.getTimeZoneShift()); <-- can fail with Zone offset minutes not in valid range: abs(value) 420 is not in the range 0 to 59
    return ZonedDateTime.ofInstant(i, offset);
  }

  public static ZonedDateTime toZonedDateTime(EventDateTime edt) {
    if (edt == null) return null;
    Long time = null;
    if (edt.getDateTime() != null) {
      time = edt.getDateTime().getValue();
    } else if (edt.getDate() != null) {
      time = edt.getDate().getValue();
    }
    if (time == null) return null;
    Instant i = Instant.ofEpochMilli(time);
    ZoneId zoneId = null;
    if (edt.getTimeZone() != null) {
      zoneId = ZoneId.of(edt.getTimeZone());
    } else {
      zoneId = ZoneId.systemDefault();
    }
    return ZonedDateTime.ofInstant(i, zoneId);
  }
}
