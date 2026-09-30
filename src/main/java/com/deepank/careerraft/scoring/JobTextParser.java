package com.deepank.careerraft.scoring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JobTextParser {
    private JobTextParser() {}

    private static final Pattern[] EXPERIENCE_PATTERNS = {
            Pattern.compile("(?:at least|minimum(?: of)?|minimum|more than)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:\\+\\s*)?(?:years?|yrs?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\+\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:-\\s*\\d+(?:\\.\\d+)?)?\\s*(?:years?|yrs?)\\s+(?:of\\s+)?experience", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern RANGE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:-|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:lpa|lakhs?|lac)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE = Pattern.compile("(?:₹|rs\\.?\\s*)?(\\d+(?:\\.\\d+)?)\\s*(?:lpa|lakhs?|lac)", Pattern.CASE_INSENSITIVE);

    public static Double parseRequiredExperience(String text) {
        for (Pattern pattern : EXPERIENCE_PATTERNS) {
            Matcher m = pattern.matcher(text == null ? "" : text);
            if (m.find()) return Double.parseDouble(m.group(1));
        }
        return null;
    }

    public static double[] parseLpaRange(String text) {
        String value = text == null ? "" : text;
        Matcher range = RANGE.matcher(value);
        if (range.find()) return new double[]{Double.parseDouble(range.group(1)), Double.parseDouble(range.group(2))};
        Matcher single = SINGLE.matcher(value);
        if (single.find()) {
            double v = Double.parseDouble(single.group(1));
            return new double[]{v, v};
        }
        return new double[]{Double.NaN, Double.NaN};
    }
}
