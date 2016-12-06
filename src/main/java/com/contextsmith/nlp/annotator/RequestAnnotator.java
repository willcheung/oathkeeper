package com.contextsmith.nlp.annotator;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.email.parser.EnglishEmailTextParser;
import com.contextsmith.email.parser.EnglishScorer;
import com.google.common.base.Stopwatch;

public class RequestAnnotator extends AbstractAnnotator {
  private static final Logger log = LoggerFactory.getLogger(RequestAnnotator.class);

  private static RequestAnnotator instance = null;

  private static final Pattern POLITE_REQUEST = Pattern.compile(
      "(?i)\\b(?:if|whether(?:\\s+or\\s+not)?)\\s+(?:you)\\s+(?:can|could|may|might|will|would)\\s+");

  public static synchronized RequestAnnotator getInstance() {
    if (instance == null) instance = new RequestAnnotator();
    return instance;
  }

  public static void main(String[] args) throws ParseException {
//    interactiveRun(getInstance());

    Stopwatch stopwatch = Stopwatch.createStarted();
    String s =
        "The user satisfaction would be one I assume, and AM finding the tool helpful - what else do you usually look at?\n\nThanks,";
//        "this is just a test mail.\r\n\r\nPlease reply before next Monday?";
        /*"Please deliver the report by tomorrow 1am. " +
        "We will discuss this in more detail next Monday. " +
        "We will discuss about the details about the project on Feb 28. " +
        "We will meet at McDonalds on Milthilda Ave on Thursday, March 3rd. " +
        "Let’s meet inside the lobby of Building 41 next thursday at 5pm. " +
        "The assignment is due Jan. 28, 2016, right before our final exam. " +
        "We will be moving the team outing to next thursday, March 3rd.";*/

    Annotation ann = new Annotation(s);
    List<Annotation> tasks = RequestAnnotator.getInstance().annotate(ann);
    log.debug("# tasks: {}", tasks.size());
    for (Annotation task : tasks) {
      log.debug("{}", task);
    }
    log.debug("Elapsed time: {}", stopwatch);
  }

  public RequestAnnotator() {
    super(RequestAnnotator.class.getSimpleName());
  }

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    List<Annotation> requests = new ArrayList<>();
    log.trace("Segmentting sentences for: {}", parent);
    List<Annotation> sentences =
        SentenceAnnotator.getInstance().annotate(parent);
    log.trace("Found {} sentences!", sentences.size());

    for (Annotation sentence : sentences) {
      // Check if this is a valid English sentence.
      double enScore =
          EnglishScorer.getInstance().computeScore(sentence.getText());
      if (enScore == 0) {
        log.trace("English score {} is less than {} for: {}",
                  enScore, EnglishEmailTextParser.MIN_SENTENCE_SCORE, sentence);
        continue;
      }
      sentence.setType(this.getAnnotatorType());
      sentence.setPriority(this.getPriority());

      log.trace("Annotating requests...");
      if (sentence.getText().trim().endsWith("?")) {
        requests.add(sentence);
      } else {
        Matcher m = POLITE_REQUEST.matcher(sentence.getText());
        if (m.find()) {
          requests.add(sentence);
//          requests.add(sentence.subAnnotation(m.start(), sentence.length()));
        }
      }
    }
    return requests;
  }
}
