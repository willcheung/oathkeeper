package com.contextsmith.nlp.email.parser;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.nlp.annotation.FirstNameAnnotator;
import com.contextsmith.nlp.annotation.LastNameAnnotator;
import com.contextsmith.nlp.annotation.SalutationAnnotator;
import com.contextsmith.nlp.annotation.ValedictionAnnotator;
import com.google.common.base.Joiner;
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
    HAS_SENDER_NAME_PREFIX,
    HAS_RECIPIENT_NAME_PREFIX,
    HAS_PERSON_NAME,
    HAS_QUOTED_PREFIX,
    HAS_EMAIL_ADDRESS,
    IS_QUOTED_HEADER,
    IS_EMPTY_LINE,
  }

  static final Logger log = LogManager.getLogger(EnglishEmailBodyParser.class);

  public static final int MAX_FEW_TOKENS = 5;
  public static final int MAX_SALUTATION_LINES = 3;
  public static final int MAX_SIGNATURE_LINES = 10;
  public static final double MIN_SENTENCE_SCORE = 0.02;
  public static final String END_LINE_RE = "\r?\n";

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

//    String plainText = "Yes. Sorry, I missed that.\r\n\r\nThanks,\r\n\r\nWilliam";
//    String plainText = "Thanks William, appreciated it,";
//    String plainText = "Thank you, appreciate your help.";
    String plainText = "You guys\r\nwill be our champion and innovator!\r\n\r\nBest,\r\n\r\nWill";

    EnglishEmailBodyParser parser = new EnglishEmailBodyParser();
    log.debug(parser.parse(plainText));
  }

  private static Pattern makeNamePattern(Set<InternetAddress> people) {
    List<String> tokens = new ArrayList<>();
    for (InternetAddress person : people) {
      String name = person.getPersonal();
      if (StringUtils.isBlank(name)) {
        name = person.getAddress().replaceFirst("@.+$", "");
      }
      String[] words = name.split("[\\p{Punct}\\s]+");
      for (String word : words) {
        if (word.length() > 2) tokens.add(word);
      }
    }
    String pattern = Joiner.on("\\E|\\Q").skipNulls().join(tokens);
    return Pattern.compile(String.format("(?i).{0,3}\\b(\\Q%s\\E)\\b", pattern));
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
    return parse(text, null, null);
  }

  public EnglishEmailBodyParser parse(String text, Set<InternetAddress> senders,
                                      Set<InternetAddress> recipients) {
    checkNotNull(text);

    Pattern senderNamePat = null;
    if (senders != null) {
      senderNamePat = makeNamePattern(senders);
      log.trace("Sender name pattern: {}", senderNamePat);
    }

    Pattern recipientNamePat = null;
    if (recipients != null) {
      recipientNamePat = makeNamePattern(recipients);
      log.trace("Recipient pattern: {}", recipientNamePat);
    }

    // Initialization.
    this.featureTable = HashBasedTable.create();
    this.lines = text.trim().split(END_LINE_RE);
    this.lineLabels = new LineCategory[this.lines.length];
    Arrays.fill(this.lineLabels, null);

    // Generate feature table.
    for (int i = 0; i < this.lines.length; ++i) {
      this.lines[i] = this.lines[i].trim();

      for (LineFeature feature : LineFeature.values()) {
//        System.out.println(String.format("%d. [%s] \"%s\"", i, feature, this.lines[i]));
        if (matchesFeature(this.lines[i], feature, senderNamePat, recipientNamePat)) {
//          System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^");
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

    // Body must have at least one line.
    /*if (bodyStartLine == bodyEndLine) {
      if (bodyStartLine > 0) --bodyStartLine;
      else if (bodyEndLine < this.lineLabels.length) ++bodyEndLine;
    }*/

    // Assign category.
    for (int i = bodyStartLine; i < bodyEndLine; ++i) {
//      Map<LineFeature, Double> map = this.featureTable.row(i);
//      if (map != null && map.containsKey(LineFeature.EMPTY_LINE)) continue;
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
      boolean condition2 = map.containsKey(LineFeature.HAS_PERSON_NAME);
      boolean condition3 = map.containsKey(LineFeature.HAS_PUNCTUATION_SUFFIX);
//      boolean condition5 = map.containsKey(LineFeature.HAS_FEW_TOKENS);
      boolean condition4 = map.containsKey(LineFeature.HAS_RECIPIENT_NAME_PREFIX);

      if (condition1 || ((condition2 || condition4) && condition3)) {
        this.lineLabels[i] = LineCategory.SALUTATION;
        break;  // Salutation usually has only one line.
      }
    }
  }

  /*private void identifySignature() {
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
  }*/

  private void identifySignature() {
    int quotedLineBeginRow = 0;
    for (; quotedLineBeginRow < this.lineLabels.length; ++quotedLineBeginRow) {
      // Examine only up to the first quoted line.
      if (this.lineLabels[quotedLineBeginRow] == LineCategory.QUOTED) break;
    }

    for (int i = 0; i < quotedLineBeginRow; ++i) {
      // If this line already has label, then skip.
      if (this.lineLabels[i] != null) continue;

      Map<LineFeature, Double> map1 = this.featureTable.row(i);
      if (map1 == null) continue;

      // Signature line must not be a sentence.
      if (map1.containsKey(LineFeature.IS_SENTENCE)) continue;

      boolean condition10 = map1.containsKey(LineFeature.IS_EMPTY_LINE);
      boolean condition11 = map1.containsKey(LineFeature.HAS_VALEDICTION_PREFIX);
      boolean condition12 = map1.containsKey(LineFeature.HAS_FEW_TOKENS);
      boolean condition13 = map1.containsKey(LineFeature.HAS_PERSON_NAME);
      boolean condition14 = map1.containsKey(LineFeature.HAS_SENDER_NAME_PREFIX);

      int nextNonEmptyLine = i + 1;
      for (; nextNonEmptyLine < quotedLineBeginRow; ++nextNonEmptyLine) {
        Map<LineFeature, Double> map = this.featureTable.row(nextNonEmptyLine);
        if (!map.containsKey(LineFeature.IS_EMPTY_LINE)) break;
      }

      if (nextNonEmptyLine < quotedLineBeginRow) {  // Has next non-empty line.
        Map<LineFeature, Double> map2 = this.featureTable.row(nextNonEmptyLine);
        if (map2 != null) {
          // Signature line must not be a sentence.
          if (map2.containsKey(LineFeature.IS_SENTENCE)) continue;

//          boolean condition20 = map2.containsKey(LineFeature.IS_EMPTY_LINE);
          boolean condition21 = map2.containsKey(LineFeature.HAS_VALEDICTION_PREFIX);
          boolean condition22 = map2.containsKey(LineFeature.HAS_FEW_TOKENS);
          boolean condition23 = map2.containsKey(LineFeature.HAS_PERSON_NAME);
          boolean condition24 = map2.containsKey(LineFeature.HAS_SENDER_NAME_PREFIX);

          if (condition11 && (condition21 || condition22 || condition23 || condition24)) {
            this.lineLabels[i] = LineCategory.SIGNATURE;
            this.lineLabels[nextNonEmptyLine] = LineCategory.SIGNATURE;
            break;  // Stop looking.
          } else if (condition10 && condition22 && (condition23 || condition24)) {
            this.lineLabels[nextNonEmptyLine] = LineCategory.SIGNATURE;
            break;  // Stop looking.
          }
        }
      } else {  // Does not have next line.
        if (condition12 && (condition13 || condition14)) {
          this.lineLabels[i] = LineCategory.SIGNATURE;
          break;
        }
      }
    }
  }

  private boolean matchesFeature(String line, LineFeature feature,
                                 Pattern senderNamePat,
                                 Pattern recipientNamePat) {
    switch (feature) {
    case IS_QUOTED_HEADER:
      // On Wed, Oct 21, 2015 at 11:02 AM, Richard Wang <rcwang@gmail.com> wrote:
      return line.matches(".+?> wrote:") ||
             line.matches(".+? 寫道﹕") ||
             line.matches(".+?> a écrit :") ||
             line.matches(".+?>:") ||
             line.matches("On .+?>");
    case HAS_EMAIL_ADDRESS:
      return line.matches(".*\\b[\\w-]+@[\\w-]+(\\.[\\w-]+)+\\b.*");
    case HAS_QUOTED_PREFIX:
      return line.startsWith(">") ||
             line.matches("(From|Sent|To|Subject): .+");
    case HAS_SALUTATION_PREFIX:
      return !SalutationAnnotator.getInstance().annotate(line).isEmpty();
    case HAS_VALEDICTION_PREFIX:
      return line.matches("-{2,3}([^-]+|$)") ||
             !ValedictionAnnotator.getInstance().annotate(line).isEmpty();
    case HAS_PUNCTUATION_SUFFIX:
      return line.endsWith(",") || line.endsWith(":") || line.endsWith("-");
    case HAS_PERSON_NAME:
      return !FirstNameAnnotator.getInstance().annotate(line).isEmpty() ||
             !LastNameAnnotator.getInstance().annotate(line).isEmpty();
    case HAS_SENDER_NAME_PREFIX:
      if (senderNamePat == null) return false;
      return senderNamePat.matcher(line).find();
    case HAS_RECIPIENT_NAME_PREFIX:
      if (recipientNamePat == null) return false;
      return recipientNamePat.matcher(line).find();
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
    case IS_EMPTY_LINE:
      return StringUtils.isBlank(line);
    default: return false;
    }
  }
}
