package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A closed date range for the query endpoints.
 *
 * Both bounds default to "the last 30 days ending today" (via the injected {@link Clock}).
 * {@link #resolve} is the single place the request parameters are checked (TODO-232):
 * when present, {@code from} and {@code to} must be real calendar dates in
 * {@code YYYY-MM-DD} form, {@code from} must be on or before {@code to}, and
 * {@code ChronoUnit.DAYS.between(from, to)} may be at most {@value #MAX_SPAN_DAYS}.
 * Problems are collected rather than thrown one at a time, so a caller can report them
 * all together as an {@link InvalidParametersException} (rendered as a 400 by
 * {@link ApiExceptionHandler}).
 *
 * The record constructor itself does not validate, so repositories and tests can still
 * build arbitrary (even backwards) ranges directly.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_SPAN_DAYS = 366;

    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    /** Resolves and validates the range, throwing if anything is wrong. */
    public static DateRange resolve(String from, String to, Clock clock) {
        List<String> errors = new ArrayList<>();
        DateRange range = resolve(from, to, clock, errors);
        InvalidParametersException.throwIfAny(errors);
        return range;
    }

    /**
     * Resolves the range, appending any problems to {@code errors} instead of throwing.
     * The returned value is only meaningful when no errors were added.
     */
    public static DateRange resolve(String from, String to, Clock clock, List<String> errors) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = isMissing(from) ? today.minusDays(DEFAULT_DAYS) : parse(from, "from", errors);
        LocalDate end = isMissing(to) ? today : parse(to, "to", errors);
        if (start != null && end != null) {
            if (start.isAfter(end)) {
                errors.add("from must be on or before to");
            } else if (ChronoUnit.DAYS.between(start, end) > MAX_SPAN_DAYS) {
                errors.add("the range may span at most " + MAX_SPAN_DAYS + " days");
            }
        }
        return new DateRange(start, end);
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate parse(String value, String name, List<String> errors) {
        try {
            return LocalDate.parse(value.trim(), ISO_DATE);
        } catch (DateTimeParseException e) {
            errors.add(name + " must be an ISO date (YYYY-MM-DD)");
            return null;
        }
    }
}
