package com.contextsmith.email.parser;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.nlp.annotation.FirstNameAnnotator;
import com.contextsmith.nlp.annotation.LastNameAnnotator;
import com.contextsmith.nlp.annotation.SalutationAnnotator;
import com.contextsmith.nlp.annotation.ValedictionAnnotator;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class EnglishEmailBodyParser {

  public enum LineCategory {
    SALUTATION,
    BODY,
    SIGNATURE,
    QUOTED
  }

  public enum LineFeature {
    IS_SENTENCE,
    IS_NOT_SENTENCE,
    HAS_FEW_TOKENS,
    HAS_MANY_TOKENS,
    HAS_SALUTATION_PREFIX,
    HAS_PUNCTUATION_SUFFIX,
    HAS_VALEDICTION_PREFIX,
    HAS_PERSON_NAME,
    HAS_QUOTED_PREFIX,
    HAS_EMAIL_ADDRESS,
    IS_QUOTED_HEADER,
    EMPTY_LINE,
  }

  static final Logger log = LogManager.getLogger(EnglishEmailBodyParser.class);

  public static final int MAX_FEW_TOKENS = 5;
  public static final int MAX_SALUTATION_LINES = 3;
  public static final int MAX_SIGNATURE_LINES = 10;
  public static final double MIN_SENTENCE_SCORE = 0.02;
  public static final String END_LINE_RE = "\r?\n";
  public static EnglishEmailBodyParser instance = null;

  public static EnglishEmailBodyParser getInstance() {
    if (instance == null) instance = new EnglishEmailBodyParser();
    return instance;
  }

  public static void main(String[] args) {
    /*List<MimeMessage> messages = EmailClustererMain.fetchEmails();
    EnglishEmailBodyParser parser = new EnglishEmailBodyParser();

    for (MimeMessage message : messages) {
      String plainText = null;
      try {
        plainText = MimeMessageUtil.extractPlainText(message);
      } catch (IOException | MessagingException e) {
        e.printStackTrace();
      }
      if (plainText == null) continue;

      log.debug(plainText);
      log.debug(parser.parse(plainText));
    }*/

    String plainText = "To whom it may concern,\r\nThanks Tim.  Can you also add Alex achen@comprehend.com please?  I'd like\r\n" +
        "to record the training, and open to your or my WebEx.\r\n" +
        "\r\n" +
        "---\r\n" +
        "Will Cheung\r\n" +
        "Director of Customer Solutions\r\n" +
        "Comprehend Systems\r\n" +
        "o: 650.600.3895 | m: 650.483.5977\r\n" +
        "\r\n" +
        "\r\n" +
        "\r\n" +
        "On Mon, Aug 25, 2014 at 1:17 PM, Roinas, Tim <\r\n" +
        "tim.roinas.contractor@astellas.com> wrote:\r\n" +
        "\r\n" +
        ">  25AUG2014 Update: Rescheduling to avoid current schedule conflicts\r\n" +
        ">\r\n" +
        "> WebEx details will be sent at a later date.\r\n" +
        ">\r\n" +
        "> Thanks.\r\n" +
        ">\r\n" +
        ">";

    EnglishEmailBodyParser parser = new EnglishEmailBodyParser();
    log.debug(parser.parse(plainText));
  }

  private String[] lines;
  private LineCategory[] lineLabels;
  private Table<Integer, LineFeature, Double> featureTable;

  public EnglishEmailBodyParser() {
    this.lines = null;
    this.lineLabels = null;
    this.featureTable = null;
  }

  public String getBody() {
    return getLinesByCategory(LineCategory.BODY);
  }

  public String getQuoted() {
    return getLinesByCategory(LineCategory.QUOTED);
  }

  public String getSalutation() {
    return getLinesByCategory(LineCategory.SALUTATION);
  }

  public String getSignature() {
    return getLinesByCategory(LineCategory.SIGNATURE);
  }

  public EnglishEmailBodyParser parse(String text) {
    checkNotNull(text);

    // Initialization.
    this.featureTable = HashBasedTable.create();
    this.lines = text.trim().split(END_LINE_RE);
    this.lineLabels = new LineCategory[this.lines.length];
    Arrays.fill(this.lineLabels, null);

    // Generate feature table.
    for (int i = 0; i < this.lines.length; ++i) {
      this.lines[i] = this.lines[i].trim();

      for (LineFeature feature : LineFeature.values()) {
        if (matchesFeature(this.lines[i], feature)) {
          this.featureTable.put(i, feature, (double) 1);
        }
      }
    }

    // Order is important here.
    identifySalutation();
    identifyQuoted();
    identifySignature();
    identifyBody();

    return this;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (LineCategory category : LineCategory.values()) {
      String content = getLinesByCategory(category);
      if (StringUtils.isBlank(content)) continue;
      builder.append(String.format("== [%s] ==%n", category));
      builder.append(content);
    }
    return builder.toString();
  }

  private String getLinesByCategory(LineCategory category) {
    if (this.lineLabels == null) {
      log.error("Must execute parse() first!");
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < this.lineLabels.length; ++i) {
      if (this.lineLabels[i] == category) {
        builder.append(this.lines[i]).append(System.lineSeparator());
      }
    }
    return builder.toString();
  }

  private void identifyBody() {
    // Start from line 0 or salutation, whichever is bigger.
    int bodyStartLine = 0;
    // End at last line or signature or quoted text, whichever is smaller.
    int bodyEndLine = this.lineLabels.length;  // Exclusive.

    for (int i = 0; i < this.lineLabels.length; ++i) {
      LineCategory category = this.lineLabels[i];
      if (category == LineCategory.SALUTATION) {
        bodyStartLine = i + 1;
      }
      if (category == LineCategory.SIGNATURE ||
          category == LineCategory.QUOTED) {
        bodyEndLine = i;
        break;
      }
    }
    // Assign category.
    for (int i = bodyStartLine; i < bodyEndLine; ++i) {
      Map<LineFeature, Double> map = this.featureTable.row(i);
      if (map != null && map.containsKey(LineFeature.EMPTY_LINE)) continue;
      this.lineLabels[i] = LineCategory.BODY;
    }
  }

  private void identifyQuoted() {
    for (int i = 0; i < this.lineLabels.length; ++i) {
      if (this.lineLabels[i] != null) continue;
      Map<LineFeature, Double> map = this.featureTable.row(i);
      if (map == null) continue;

      boolean condition1 = map.containsKey(LineFeature.IS_QUOTED_HEADER) &&
                           map.containsKey(LineFeature.HAS_EMAIL_ADDRESS);
      boolean condition2 = map.containsKey(LineFeature.HAS_QUOTED_PREFIX);

      if (condition1 || condition2) {
        this.lineLabels[i] = LineCategory.QUOTED;
      }
    }
  }

  private void identifySalutation() {
    for (int i = 0; i < Math.min(MAX_SALUTATION_LINES, this.lineLabels.length); ++i) {
      if (this.lineLabels[i] != null) continue;
      Map<LineFeature, Double> map = this.featureTable.row(i);
      if (map == null) continue;

      // Salutation must not be a sentence.
      if (map.containsKey(LineFeature.HAS_MANY_TOKENS)) break;

      boolean condition1 = map.containsKey(LineFeature.HAS_SALUTATION_PREFIX);
      boolean condition2 = map.containsKey(LineFeature.HAS_PERSON_NAME) &&
                           map.containsKey(LineFeature.HAS_PUNCTUATION_SUFFIX);

      if (condition1 || condition2) {
        this.lineLabels[i] = LineCategory.SALUTATION;
        break;  // Salutation usually has only one line.
      }
    }
  }

  private void identifySignature() {
    // Find out where the signature's bottom line is.
    int bottomLine = this.lineLabels.length - 1;
    for (int i = 0; i < this.lineLabels.length; ++i) {
      if (this.lineLabels[i] == LineCategory.QUOTED) {
        if (i == 0) return;
        bottomLine = i - 1;
        break;
      }
    }

    // Start from the bottom line and scan upwards line-by-line.
    for (int i = bottomLine;
         i >= Math.max(bottomLine - MAX_SIGNATURE_LINES, 0); --i) {
      if (this.lineLabels[i] != null) continue;
      Map<LineFeature, Double> map = this.featureTable.row(i);
      if (map == null) continue;

      // Signature line must not be a sentence.
      if (map.containsKey(LineFeature.IS_SENTENCE)) break;

      boolean condition1 = map.containsKey(LineFeature.HAS_VALEDICTION_PREFIX);
      boolean condition2 = map.containsKey(LineFeature.HAS_FEW_TOKENS);
      boolean condition3 = map.containsKey(LineFeature.HAS_PERSON_NAME);

      if (condition1) {
        this.lineLabels[i] = LineCategory.SIGNATURE;
        break;  // Stop looking.
      } else if (condition2 || condition3) {
        this.lineLabels[i] = LineCategory.SIGNATURE;
      }
    }
  }

  private boolean matchesFeature(String line, LineFeature feature) {
    switch (feature) {
    case IS_QUOTED_HEADER:
      // On Wed, Oct 21, 2015 at 11:02 AM, Richard Wang <rcwang@gmail.com> wrote:
      return line.matches(".+?> wrote:") ||
             line.matches(".+? 寫道﹕") ||
             line.matches(".+?> a écrit :") ||
             line.matches(".+?>:");
    case HAS_EMAIL_ADDRESS:
      return line.matches(".*\\b[\\w-]+@[\\w-]+(\\.[\\w-]+)+\\b.*");
    case HAS_QUOTED_PREFIX:
      return line.startsWith(">");
    case HAS_SALUTATION_PREFIX:
      return !SalutationAnnotator.getInstance().annotate(line).isEmpty();
    case HAS_VALEDICTION_PREFIX:
      return line.matches("-{2,3}([^-]+|$)") ||
             !ValedictionAnnotator.getInstance().annotate(line).isEmpty();
    case HAS_PUNCTUATION_SUFFIX:
      return line.endsWith(",") || line.endsWith(":");
    case HAS_PERSON_NAME:
      return !FirstNameAnnotator.getInstance().annotate(line).isEmpty() ||
             !LastNameAnnotator.getInstance().annotate(line).isEmpty();
    case HAS_FEW_TOKENS:
      return StringUtils.isNotBlank(line) &&
             EnglishScorer.tokenize(line).size() <= MAX_FEW_TOKENS;
    case HAS_MANY_TOKENS:
      return StringUtils.isNotBlank(line) &&
             EnglishScorer.tokenize(line).size() > MAX_FEW_TOKENS;
    case IS_SENTENCE:
      return StringUtils.isNotBlank(line) &&
             EnglishScorer.getInstance().computeScore(line) >= MIN_SENTENCE_SCORE;
    case IS_NOT_SENTENCE:
      return StringUtils.isNotBlank(line) &&
             EnglishScorer.getInstance().computeScore(line) < MIN_SENTENCE_SCORE;
    case EMPTY_LINE:
      return StringUtils.isBlank(line);
    default: return false;
    }
  }
}
