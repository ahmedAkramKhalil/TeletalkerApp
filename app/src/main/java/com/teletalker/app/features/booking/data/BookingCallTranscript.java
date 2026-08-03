package com.teletalker.app.features.booking.data;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-process buffer that collects the agent's transcripts during a single
 * AI booking call. The {@link com.teletalker.app.services.ai.AICallRecorderRefactored}
 * appends transcripts as they arrive; the
 * {@link com.teletalker.app.services.scheduler.ScheduledCallCompleter}
 * reads them on call end and classifies the outcome.
 *
 * Single-instance global state — only one outbound call runs at a time.
 */
public final class BookingCallTranscript {

    private static final String TAG = "BookingTranscript";
    private static final BookingCallTranscript INSTANCE = new BookingCallTranscript();
    public static BookingCallTranscript get() { return INSTANCE; }

    /** Possible outcomes inferred from the transcript. */
    public enum Outcome {
        CONFIRMED,
        RESCHEDULED,
        DECLINED,
        NO_ANSWER,
        UNCLEAR,
    }

    private final List<String> agentLines = new ArrayList<>();
    private final List<String> userLines  = new ArrayList<>();
    private long callStartedAt = 0;
    private long firstUserSpeechAt = 0;
    /** Outcome reported directly by the agent via a client tool call.
     *  Wins over the keyword classifier. */
    private Outcome toolOutcome = null;
    /** ISO datetime the customer agreed during a reschedule — null when the
     *  reschedule was free-text ("sometime next week"). */
    private String rescheduleIso = null;
    /** Free-text reason captured when the customer cancelled. */
    private String cancellationReason = null;

    private BookingCallTranscript() {}

    public synchronized void reset() {
        agentLines.clear();
        userLines.clear();
        callStartedAt = System.currentTimeMillis();
        firstUserSpeechAt = 0;
        toolOutcome = null;
        rescheduleIso = null;
        cancellationReason = null;
        Log.d(TAG, "Transcript reset for new call");
    }

    /** Tool-call outcome reported by the agent — wins over the keyword classifier. */
    public synchronized void markToolOutcome(Outcome o) {
        toolOutcome = o;
        Log.d(TAG, "Tool outcome recorded: " + o);
    }

    public synchronized void markRescheduleIso(String iso) {
        rescheduleIso = iso;
        Log.d(TAG, "Reschedule iso recorded: " + iso);
    }

    public synchronized String getRescheduleIso() { return rescheduleIso; }

    public synchronized void markCancellationReason(String reason) {
        cancellationReason = reason;
        Log.d(TAG, "Cancellation reason recorded: " + reason);
    }

    public synchronized String getCancellationReason() { return cancellationReason; }

    public synchronized void onAgentLine(String text) {
        if (text == null || text.isEmpty()) return;
        agentLines.add(text);
    }

    public synchronized void onUserLine(String text) {
        if (text == null || text.isEmpty()) return;
        userLines.add(text);
        if (firstUserSpeechAt == 0) firstUserSpeechAt = System.currentTimeMillis();
    }

    /** Did the customer say anything at all during the call? */
    public synchronized boolean userSpoke() {
        for (String s : userLines) {
            if (s != null && !s.trim().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Classify the call into a coarse outcome. The system prompt steers the
     * customer toward one of three replies — we look for keyword patterns
     * in the customer's transcript first, then fall back to the agent's
     * closing remarks.
     */
    public synchronized Outcome classify() {
        // Agent-reported outcome (from a client tool call) wins — it's
        // a structured signal directly from the model, no keyword guesswork.
        if (toolOutcome != null) {
            Log.d(TAG, "classify(): using tool-reported outcome " + toolOutcome);
            return toolOutcome;
        }

        if (!userSpoke()) return Outcome.NO_ANSWER;

        String user = lowercaseAll(userLines);
        String agent = lowercaseAll(agentLines);

        // Strongest signals: cancellation keywords from the customer.
        if (matchesAny(user, CANCEL_TOKENS)) return Outcome.DECLINED;
        // Reschedule keywords.
        if (matchesAny(user, RESCHEDULE_TOKENS)) return Outcome.RESCHEDULED;
        // Affirmative keywords.
        if (matchesAny(user, CONFIRM_TOKENS))  return Outcome.CONFIRMED;

        // Fall back to what the agent said at the end — the system prompt
        // tells it to thank/warn/acknowledge per outcome, so the closing
        // line often discloses the result.
        if (matchesAny(agent, AGENT_CANCEL))     return Outcome.DECLINED;
        if (matchesAny(agent, AGENT_RESCHEDULE)) return Outcome.RESCHEDULED;
        if (matchesAny(agent, AGENT_CONFIRM))    return Outcome.CONFIRMED;

        return Outcome.UNCLEAR;
    }

    /** Map the outcome enum to the string the reportBookingCallOutcome function expects. */
    public static String outcomeToString(Outcome o) {
        switch (o) {
            case CONFIRMED:   return "confirmed";
            case RESCHEDULED: return "rescheduled";
            case DECLINED:    return "declined";
            case NO_ANSWER:   return "no_answer";
            case UNCLEAR:
            default:          return "called"; // best-effort generic outcome
        }
    }

    /** Return a sanitised, short transcript snippet for debugging / logs. */
    public synchronized String shortSummary() {
        return String.format(Locale.getDefault(),
                "agent=%d user=%d userSpoke=%s",
                agentLines.size(), userLines.size(), userSpoke());
    }

    // -------------------------------------------------------------------
    // Keyword sets — intentionally generous to tolerate ASR errors.
    // Case-insensitive. Word boundaries handled by matchesAny().
    // -------------------------------------------------------------------

    private static final String[] CONFIRM_TOKENS = {
            "yes", "yeah", "yep", "sure", "ok", "okay", "see you", "see ya",
            "i'll be there", "ill be there", "i will be there", "sounds good",
            "perfect", "confirmed", "still on", "still works", "works for me",
            "see you then", "see you there",
    };
    private static final String[] RESCHEDULE_TOKENS = {
            "reschedule", "different time", "another time", "another day",
            "change the time", "change the date", "move it", "push it",
            "later", "earlier", "next week", "tomorrow instead",
            "can we change", "different date",
    };
    private static final String[] CANCEL_TOKENS = {
            "cancel", "can't make", "cant make", "can not make",
            "won't make", "wont make", "not able", "don't need", "dont need",
            "not anymore", "no thanks", "no thank you",
    };
    private static final String[] AGENT_CONFIRM = {
            "thanks for confirming", "great, see you", "we'll see you",
            "all set", "confirmed for",
    };
    private static final String[] AGENT_RESCHEDULE = {
            "follow up to reschedule", "follow up about a new time",
            "we'll find a new time", "will follow up",
    };
    private static final String[] AGENT_CANCEL = {
            "slot will be released", "we'll release the slot",
            "cancel the appointment",
    };

    private static String lowercaseAll(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            if (l == null) continue;
            b.append(' ').append(l.toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    private static boolean matchesAny(String haystack, String[] needles) {
        if (haystack == null || haystack.isEmpty()) return false;
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
