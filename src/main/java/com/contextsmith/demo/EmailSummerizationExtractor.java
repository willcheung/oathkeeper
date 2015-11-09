package com.contextsmith.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class EmailSummerizationExtractor {

  public static final int MIN_SENTENCE_WORDCOUNT = 10;
  private static final Logger log = 
      LogManager.getLogger(EmailSummerizationExtractor.class);
  private StanfordCoreNLP pipeline;
  
  public EmailSummerizationExtractor() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    props.setProperty("ssplit.newlineIsSentenceBreak", "two");
    this.pipeline = new StanfordCoreNLP(props);
  }
  
  public String extract(String content) {
    log.debug("Message:\n{}", content);
    // Create an empty Annotation just with the given text
    Annotation document = new Annotation(content);
    // Run all Annotators on this text
    pipeline.annotate(document);
    String summerization = "";
    String backupCandidate = "";
    int backupCandidateWordCount = Integer.MIN_VALUE;
    // These are all the sentences in this document
    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      int count = getTokensFromSentence(sentence).size();
      if (count > MIN_SENTENCE_WORDCOUNT) {
        summerization = sentence.toString();
        break;
      }
      if (backupCandidateWordCount < count) {
        backupCandidate = sentence.toString();
        backupCandidateWordCount = count;
      }
    }
    // Use the backup candidate.
    if (StringUtils.isEmpty(summerization)) {
      summerization = backupCandidate;
    }
    
    log.debug("summerization: {}  lines: {}", 
        TextUtil.replaceNewlines(summerization), 
        TextUtil.countLines(summerization));
       
    return summerization;
  }
  
  public List<String> getTokensFromSentence(CoreMap sentence) {
    List<String> tokens = new ArrayList<>();
    for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
      // this is the text of the token
      tokens.add(token.get(TextAnnotation.class));
    }
    return tokens; 
  }
}
