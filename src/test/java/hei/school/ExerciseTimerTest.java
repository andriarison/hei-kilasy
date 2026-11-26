package hei.school;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExerciseTimerTest {
    private static final LocalDateTime STARTED = LocalDateTime.parse("2026-11-26T10:00:00");

    @ParameterizedTest
    @CsvSource({
        "30,2026-11-26T10:00:00,false",
        "30,2026-11-26T10:29:59,false",
        "30,2026-11-26T10:30:00,false",
        "30,2026-11-26T10:30:01,true",
        "90,2026-11-26T11:29:00,false",
        "90,2026-11-26T11:31:00,true",
        "1,2026-11-27T10:00:00,true"
    })
    void submissionIsAcceptedUntilTheEndInstantIncluded(int minutes, String now, boolean expired) {
        assertEquals(expired, ExerciseTimer.isExpired(STARTED, minutes, LocalDateTime.parse(now)));
    }

    @ParameterizedTest
    @CsvSource({"30,2026-11-26T10:30:00", "1440,2026-11-27T10:00:00"})
    void endIsStartPlusDuration(int minutes, String end) {
        assertEquals(LocalDateTime.parse(end), ExerciseTimer.endsAt(STARTED, minutes));
    }

    @ParameterizedTest
    @CsvSource({
        "30,2026-11-26T10:12:05,2026-11-26T11:00:00,12 min 05 s",
        "30,2026-11-26T10:30:00,2026-11-26T11:00:00,30 min 00 s",
        "30,,2026-11-26T10:07:30,7 min 30 s",
        "30,,2026-11-26T12:00:00,30 min 00 s",
        "90,2026-11-26T11:29:59,2026-11-26T12:00:00,89 min 59 s"
    })
    void timeUsedStopsAtSubmissionOrAtTheEndOfTheDuration(int minutes, String submittedAt, String now, String expected) {
        Duration used = ExerciseTimer.timeUsed(STARTED, minutes,
                submittedAt == null ? null : LocalDateTime.parse(submittedAt), LocalDateTime.parse(now));
        assertEquals(expected, ExerciseTimer.format(used));
    }
}
