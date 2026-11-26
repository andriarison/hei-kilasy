package hei.school;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/** C3: timed exercises, tested with mocked JDBC and a fixed clock. */
class TimedExerciseTest {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final LocalDateTime STARTED = LocalDateTime.parse("2026-11-26T10:00:00");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    /** Simulated exercise_start table: first start only, like ON CONFLICT DO NOTHING. */
    private final List<Timestamp> starts = new ArrayList<>();
    private int existingSubmissions = 0;

    private static Clock at(LocalDateTime now) {
        return Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
    }

    private static Exercise exercise(Integer durationMinutes) {
        Exercise exercise = new Exercise();
        exercise.setId(7L);
        exercise.setTitle("Timed");
        exercise.setMaxPoints(20);
        exercise.setDurationMinutes(durationMinutes);
        return exercise;
    }

    @SuppressWarnings("unchecked")
    private ExerciseController exerciseController(Exercise exercise, LocalDateTime now) {
        when(jdbc.query(contains("FROM exercise WHERE id"), any(RowMapper.class), eq(7L)))
                .thenReturn(exercise == null ? List.of() : List.of(exercise));
        when(jdbc.queryForObject(contains("FROM student"), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM submission"), eq(Integer.class), eq(7L), eq(2L)))
                .thenAnswer(call -> existingSubmissions);
        when(jdbc.update(contains("INSERT INTO exercise_start"), eq(7L), eq(2L), any(Timestamp.class)))
                .thenAnswer(call -> {
                    if (!starts.isEmpty()) {
                        return 0;
                    }
                    starts.add(call.getArgument(3));
                    return 1;
                });
        when(jdbc.queryForObject(contains("SELECT started_at"), eq(Timestamp.class), eq(7L), eq(2L)))
                .thenAnswer(call -> starts.get(0));
        ExerciseController controller = new ExerciseController();
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(controller, "clock", at(now));
        return controller;
    }

    private SubmissionController submissionController(Integer durationMinutes, Date deadline, LocalDateTime now) {
        Map<String, Object> exerciseRow = new HashMap<>();
        exerciseRow.put("id", 7L);
        exerciseRow.put("deadline", deadline);
        exerciseRow.put("duration_minutes", durationMinutes);
        when(jdbc.queryForList(contains("FROM exercise WHERE id"), eq(7L))).thenReturn(List.of(exerciseRow));
        when(jdbc.queryForList(contains("FROM student WHERE id"), eq(2L))).thenReturn(List.of(Map.of("id", 2L)));
        when(jdbc.queryForObject(contains("FROM submission"), eq(Integer.class), eq(7L), eq(2L))).thenReturn(0);
        when(jdbc.queryForList(contains("SELECT started_at"), eq(Timestamp.class), eq(7L), eq(2L)))
                .thenAnswer(call -> List.copyOf(starts));
        when(jdbc.queryForObject(contains("max(id)"), eq(Long.class))).thenReturn(13L);
        SubmissionController controller = new SubmissionController();
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(controller, "mailSender", mock(JavaMailSender.class));
        ReflectionTestUtils.setField(controller, "smsSender", mock(SmsSender.class));
        ReflectionTestUtils.setField(controller, "clock", at(now));
        return controller;
    }

    private static Map<?, ?> body(ResponseEntity<?> response) {
        return (Map<?, ?>) response.getBody();
    }

    private ResponseEntity<?> submit(SubmissionController controller) {
        return controller.submit(7L, Map.of("studentId", 2, "content", "class Answer {}"));
    }

    @Test
    void startCreatesTheCountdownOnceAndKeepsTheFirstStart() {
        ResponseEntity<?> first = exerciseController(exercise(30), STARTED).start(7L, Map.of("studentId", 2));
        assertEquals(201, first.getStatusCode().value());
        assertEquals(STARTED, body(first).get("startedAt"));
        assertEquals(LocalDateTime.parse("2026-11-26T10:30:00"), body(first).get("endsAt"));

        ResponseEntity<?> again = exerciseController(exercise(30), STARTED.plusMinutes(20)).start(7L, Map.of("studentId", 2));
        assertEquals(200, again.getStatusCode().value());
        assertEquals(STARTED, body(again).get("startedAt"), "starting again must not reset the countdown");
        assertEquals(1, starts.size());
    }

    @Test
    void startRejectsUntimedUnknownOrAlreadySubmittedExercises() {
        assertEquals(422, exerciseController(exercise(null), STARTED).start(7L, Map.of("studentId", 2)).getStatusCode().value());
        assertEquals(404, exerciseController(null, STARTED).start(7L, Map.of("studentId", 2)).getStatusCode().value());
        assertEquals(400, exerciseController(exercise(30), STARTED).start(7L, Map.of()).getStatusCode().value());
        existingSubmissions = 1;
        assertEquals(409, exerciseController(exercise(30), STARTED).start(7L, Map.of("studentId", 2)).getStatusCode().value());
        assertTrue(starts.isEmpty());
    }

    @Test
    void timedSubmissionWithoutStartIsRefused() {
        ResponseEntity<?> response = submit(submissionController(30, null, STARTED));
        assertEquals(422, response.getStatusCode().value());
        assertTrue(((String) body(response).get("error")).contains("/start"));
        verify(jdbc, never()).update(contains("INSERT INTO submission"), any(), any(), any(), any(), any());
    }

    @Test
    void timedSubmissionIsAcceptedUntilTheEndIncluded() {
        starts.add(Timestamp.valueOf(STARTED));
        ResponseEntity<?> response = submit(submissionController(30, null, STARTED.plusMinutes(30)));
        assertEquals(201, response.getStatusCode().value());
        assertEquals(false, body(response).get("late"));
        verify(jdbc).update(contains("INSERT INTO submission"), eq(7L), eq(2L), any(), any(),
                eq(Timestamp.valueOf(STARTED.plusMinutes(30))));
    }

    @Test
    void timedSubmissionAfterTheDurationIsRefusedWithExplicitMessage() {
        starts.add(Timestamp.valueOf(STARTED));
        ResponseEntity<?> response = submit(submissionController(30, null, STARTED.plusMinutes(30).plusSeconds(1)));
        assertEquals(422, response.getStatusCode().value());
        assertTrue(((String) body(response).get("error")).contains("Temps écoulé"));
        assertEquals(LocalDateTime.parse("2026-11-26T10:30:00"), body(response).get("endsAt"));
        verify(jdbc, never()).update(contains("INSERT INTO submission"), any(), any(), any(), any(), any());
    }

    @Test
    void durationAndDeadlineTogetherKeepTheLatePenaltyRule() {
        starts.add(Timestamp.valueOf(STARTED));
        ResponseEntity<?> response = submit(submissionController(30, Date.valueOf("2026-11-25"), STARTED.plusMinutes(10)));
        assertEquals(201, response.getStatusCode().value());
        assertEquals(true, body(response).get("late"));
    }

    @Test
    void untimedExerciseSubmissionIsUnchanged() {
        ResponseEntity<?> response = submit(submissionController(null, Date.valueOf("2026-11-30"), STARTED));
        assertEquals(201, response.getStatusCode().value());
        assertEquals(false, body(response).get("late"));
        verify(jdbc, never()).queryForList(contains("SELECT started_at"), eq(Timestamp.class), any(), any());
    }

    @Test
    void timedOnlyExerciseHasNoLatePenalty() {
        assertEquals(new BigDecimal("16.0"), GradeCalculator.calculate(new BigDecimal("16"), new BigDecimal("20"),
                null, LocalDateTime.parse("2027-06-01T12:00:00")));
    }

    @Test
    void transcriptAndCsvHandleExercisesWithoutDeadline() {
        Map<String, Object> graded = new HashMap<>(Map.of(
                "title", "Timed", "submission_id", 13L, "max_points", new BigDecimal("20"),
                "submitted_at", Timestamp.valueOf(STARTED.plusMinutes(10)), "raw_points", new BigDecimal("16.00"),
                "duration_minutes", 30, "reference", "STD24002", "full_name", "Bob"));
        graded.put("deadline", null);
        graded.put("late", null);
        Map<String, Object> notSubmitted = new HashMap<>(Map.of("title", "Timed, not submitted"));
        notSubmitted.put("deadline", null);
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of(graded, notSubmitted));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(graded));

        GradeReport report = new GradeReport();
        ReflectionTestUtils.setField(report, "jdbcTemplate", jdbc);
        List<?> lines = (List<?>) report.build(2L).get("exercises");
        assertEquals(new BigDecimal("16.0"), ((Map<?, ?>) lines.get(0)).get("grade"));
        assertEquals(false, ((Map<?, ?>) lines.get(0)).get("late"));
        assertEquals("À RENDRE", ((Map<?, ?>) lines.get(1)).get("status"));

        CsvExport export = new CsvExport();
        ReflectionTestUtils.setField(export, "jdbcTemplate", jdbc);
        String csv = export.export().getBody();
        assertTrue(csv.contains(";Timed;;"), csv);
        assertTrue(csv.endsWith(";non;16,0\n"), csv);
    }

    @Test
    void createAcceptsDurationInsteadOfDeadlineAndValidatesIt() {
        ExerciseController controller = exerciseController(exercise(30), STARTED);
        when(jdbc.queryForObject(contains("max(id)"), eq(Long.class))).thenReturn(6L);
        ResponseEntity<?> created = controller.create(Map.of("title", "Palindrome", "durationMinutes", 45));
        assertEquals(201, created.getStatusCode().value());
        assertEquals(45, body(created).get("durationMinutes"));
        assertNull(body(created).get("deadline"));
        verify(jdbc).update(contains("duration_minutes"), eq(1L), eq("Palindrome"), isNull(), eq(20.0), isNull(), eq(45));

        assertEquals(400, controller.create(Map.of("title", "No limit")).getStatusCode().value());
        assertEquals(400, controller.create(Map.of("title", "Zero", "durationMinutes", 0)).getStatusCode().value());
        assertEquals(400, controller.create(Map.of("title", "Decimal", "durationMinutes", 1.5)).getStatusCode().value());
    }
}
