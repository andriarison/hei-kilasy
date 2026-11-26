package hei.school;

import java.time.Duration;
import java.time.LocalDateTime;

/** Countdown rule for timed exercises (C3): the student must submit before the duration ends. */
public final class ExerciseTimer {
    private ExerciseTimer() {}

    public static LocalDateTime endsAt(LocalDateTime startedAt, int durationMinutes) {
        return startedAt.plusMinutes(durationMinutes);
    }

    /** A submission at exactly the end instant is still accepted. */
    public static boolean isExpired(LocalDateTime startedAt, int durationMinutes, LocalDateTime now) {
        return now.isAfter(endsAt(startedAt, durationMinutes));
    }

    /**
     * Time used by the student: until the submission if there is one,
     * otherwise until now, never more than the duration.
     */
    public static Duration timeUsed(LocalDateTime startedAt, int durationMinutes,
                                    LocalDateTime submittedAt, LocalDateTime now) {
        LocalDateTime end = submittedAt != null ? submittedAt : now;
        Duration used = Duration.between(startedAt, end);
        Duration max = Duration.ofMinutes(durationMinutes);
        if (used.isNegative()) {
            return Duration.ZERO;
        }
        return used.compareTo(max) > 0 ? max : used;
    }

    /** Readable form for the transcript, for example "12 min 05 s". */
    public static String format(Duration duration) {
        return String.format("%d min %02d s", duration.toMinutes(), duration.toSecondsPart());
    }
}
