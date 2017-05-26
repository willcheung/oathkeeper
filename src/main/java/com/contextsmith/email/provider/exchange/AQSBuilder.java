package com.contextsmith.email.provider.exchange;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by beders on 4/26/17.
 */
public class AQSBuilder {
    StringBuilder sb;

    DateFormat format = new SimpleDateFormat("MM/dd/yy");

    public AQSBuilder(String... queryStart) {
        sb = new StringBuilder();
        for (String s : queryStart) {
            append(s);
        }
    }

    public AQSBuilder sentBetween(Date afterDate, Date beforeDate) {
        append("sent:" + format.format(afterDate) + ".." + format.format(beforeDate));
        return this;
    }

    public AQSBuilder subject(String subject) {
        append("subject:").append(quote(subject));
        return this;
    }

    /** Quote a string to make if safe for search. If the string has spaces or a colon, it will be surrounded by double-quotes.
     *
     * @param aString string to quote
     * @return quoted string
     */
    public static String quote(String aString) {
        if (aString.contains(" ") || aString.contains(":")) {
            return "\"" + aString + "\"";
        } else {
            return aString;
        }
    }

    private AQSBuilder append(String s) {
        if (sb.length() != 0) {
            sb.append(" ");
        }
        sb.append(s);
        return this;
    }

    public String toQuery() {
        return sb.toString();
    }
}
