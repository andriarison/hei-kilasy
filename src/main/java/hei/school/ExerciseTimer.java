package hei.school;

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
}
