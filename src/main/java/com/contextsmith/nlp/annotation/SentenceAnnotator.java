package com.contextsmith.nlp.annotation;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.contextsmith.utils.FileUtil;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

public class SentenceAnnotator extends AbstractAnnotator {

//  private static final Logger log = LogManager.getLogger(SentenceAnnotator.class);
  public static final String EN_SENTENCE_DETECTOR_MODEL = "en-sent.bin";
  private static SentenceAnnotator instance = null;

  public static synchronized SentenceAnnotator getInstance() {
    if (instance == null) {
      instance = new SentenceAnnotator();
    }
    return instance;
  }

  public static void main(String[] args) {
    interactiveRun(getInstance());
  }

  private SentenceDetectorME sentenceDetector;

  public SentenceAnnotator() {
    super(SentenceAnnotator.class.getSimpleName());

    InputStream modelIs = FileUtil.findResourceAsStream(EN_SENTENCE_DETECTOR_MODEL);
    SentenceModel model = null;
    try {
      model = new SentenceModel(modelIs);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (modelIs != null) {
        try { modelIs.close(); }
        catch (IOException e) {}
      }
    }
    checkNotNull(model);
    this.sentenceDetector = new SentenceDetectorME(model);
  }

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    Span[] spans = this.sentenceDetector.sentPosDetect(parent.getText());
    List<Annotation> annotations = new ArrayList<>();
    for (Span span : spans) {
      Annotation ann = parent.subAnnotation(span.getStart(), span.getEnd());
      ann.setType(super.getAnnotatorType());
      annotations.add(ann);
    }
    return annotations;
  }

}
