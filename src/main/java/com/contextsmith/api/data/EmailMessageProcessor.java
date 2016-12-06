package com.contextsmith.api.data;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.helper.StringUtil;

import com.contextsmith.email.parser.EnglishEmailTextParser;
import com.contextsmith.nlp.annotator.Annotation;
import com.contextsmith.nlp.annotator.RequestAnnotator;
import com.contextsmith.nlp.sentiment.SentimentItem;
import com.contextsmith.nlp.sentiment.SentimentScorer;
import com.contextsmith.nlp.time.TaskAnnotator;
import com.contextsmith.nlp.time.TemporalItem;
import com.contextsmith.utils.AnnotationUtil;
import com.google.common.collect.Sets;

public class EmailMessageProcessor {

  public static List<SentimentItem> analyzeSentiment(
      EmailMessage mail,
      Double posSentimentThreshold,
      Double negSentimentThreshold) {
    if (StringUtils.isBlank(mail.getContent().body)) return null;
    List<SentimentItem> sentences = null;
    try {
      sentences = SentimentScorer.process(mail.getContent().body);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (sentences == null) return null;

    SentimentItem mostPosItem = null;
    SentimentItem mostNegItem = null;
    List<SentimentItem> results = new ArrayList<>();

    for (SentimentItem item : sentences) {
      if (posSentimentThreshold != null &&
          item.score > posSentimentThreshold &&
          (mostPosItem == null || item.score > mostPosItem.score)) {
        mostPosItem = item;
      } else if (negSentimentThreshold != null &&
          item.score < negSentimentThreshold &&
          (mostNegItem == null || item.score < mostNegItem.score)) {
        mostNegItem = item;
      }
    }
    if (mostPosItem != null) results.add(mostPosItem);
    if (mostNegItem != null) results.add(mostNegItem);

    return results.isEmpty() ? null : results;
  }

  public static List<Annotation> annotateKeywords(EmailMessage mail,
                                                  Pattern searchPattern) {
    List<Annotation> annotations = new ArrayList<>();
    Annotation parent = null;

    // Requires e-mail headers: from, to, cc fields.
    String header = Sets.union(mail.getFrom(), mail.getAllRecipients()).toString();
    parent = new Annotation(header);
    parent.setType("header");
    annotations.addAll(AnnotationUtil.match(searchPattern, parent));

    // E-mail salutation.
    if (StringUtils.isNotBlank(mail.getContent().salutation)) {
      parent = new Annotation(mail.getContent().salutation);
      parent.setType("salutation");
      annotations.addAll(AnnotationUtil.match(searchPattern, parent));
    }

    // E-mail body.
    if (StringUtils.isNotBlank(mail.getContent().body)) {
      parent = new Annotation(mail.getContent().body);
      parent.setType("body");
      annotations.addAll(AnnotationUtil.match(searchPattern, parent));
    }

    if (StringUtils.isNotBlank(mail.getContent().signature)) {
      parent = new Annotation(mail.getContent().signature);
      parent.setType("signature");
      annotations.addAll(AnnotationUtil.match(searchPattern, parent));
    }

    return annotations.isEmpty() ? null : annotations;
  }

  public static List<Annotation> annotateRequests(EmailMessage mail) {
    if (StringUtils.isBlank(mail.getContent().body)) return null;
    List<Annotation> requests =
        RequestAnnotator.getInstance().annotate(mail.getContent().body);
    return requests.isEmpty() ? null : requests;
  }

  public static MailContent parseEmailText(AbstractMessage mail) {
    if (StringUtil.isBlank(mail.getPlainText())) return null;

    EnglishEmailTextParser parser = new EnglishEmailTextParser();
    parser.parse(mail.getPlainText(), mail.getFrom(), mail.getAllRecipients());

    MailContent content = new MailContent();
    content.salutation = parser.getSalutation().replaceAll("[ ]+", " ").trim();
    content.body = parser.getBody().replaceAll("[ ]+", " ").trim();
    content.signature = parser.getSignature().replaceAll("[ ]+", " ").trim();
    return content;
  }

  // Must execute after parseEmailBody.
  public static List<TemporalItem> parseTemporalItems(EmailMessage mail) {
    // Find temporal items.
    ZonedDateTime zdt = mail.getSentDate();
    if (zdt == null) return null;
    if (StringUtils.isBlank(mail.getContent().body)) return null;

    List<Annotation> taskAnns =
        TaskAnnotator.getInstance().annotate(mail.getContent().body, zdt);
    List<TemporalItem> temporalItems = new ArrayList<>();
    for (Annotation taskAnn : taskAnns) {
      TemporalItem item = TemporalItem.newInstance(taskAnn);
      if (item == null) continue;
      temporalItems.add(item);
    }
    return temporalItems.isEmpty() ? null : temporalItems;
  }

}
