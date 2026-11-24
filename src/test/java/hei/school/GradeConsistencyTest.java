package hei.school;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

class GradeConsistencyTest {
    @ParameterizedTest
    @CsvSource({
        "2026-11-10 23:59:59,16,16.0,false",
        "2026-11-11 02:00:00,16,14.4,true",
        "2026-11-13 10:00:00,16,11.2,true",
        "2026-11-18 15:00:00,16,8.0,true",
        "2026-11-11 02:00:00,13.37,12.0,true",
        "2026-11-11 02:00:00,13.388,12.1,true"
    })
    void correctionDetailTranscriptAverageEmailAndCsvShareTheSameRoundedGrade(
            String submittedAt, String raw, String expected, boolean late) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JavaMailSender mail = mock(JavaMailSender.class);
        Map<String, Object> row = new HashMap<>(Map.of(
                "id", 2L, "submission_id", 2L, "student_id", 2L,
                "title", "Exercise", "deadline", Date.valueOf("2026-11-10"),
                "submitted_at", Timestamp.valueOf(submittedAt),
                "raw_points", new BigDecimal(raw).setScale(2, java.math.RoundingMode.HALF_UP), "max_points", new BigDecimal("20"),
                "full_name", "Test Student", "email", "student@example.test"));
        row.put("phone", "+261 34 00 000 02");
        row.put("reference", "STD2");
        row.put("cohort", "K1");
        row.put("late", late);
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of(row));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row));

        SubmissionController controller = new SubmissionController();
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(controller, "mailSender", mail);
        SmsSender sms = mock(SmsSender.class);
        ReflectionTestUtils.setField(controller, "smsSender", sms);
        Map<?, ?> correction = (Map<?, ?>) controller.grade(2L,
                Map.of("points", new BigDecimal(raw), "feedback", "Good")).getBody();
        assertEquals(new BigDecimal(expected), correction.get("grade"));
        Map<?, ?> detail = (Map<?, ?>) controller.get(2L).getBody();
        assertEquals(correction.get("grade"), detail.get("grade"));

        GradeReport report = new GradeReport();
        ReflectionTestUtils.setField(report, "jdbcTemplate", jdbc);
        Map<String, Object> transcript = report.build(2L);
        Map<?, ?> line = (Map<?, ?>) ((List<?>) transcript.get("exercises")).get(0);
        assertEquals(correction.get("grade"), line.get("grade"));
        assertEquals(new BigDecimal(expected).setScale(2), transcript.get("average"));

        CsvExport export = new CsvExport();
        ReflectionTestUtils.setField(export, "jdbcTemplate", jdbc);
        assertTrue(export.export().getBody().contains(";" + (late ? "oui" : "non") + ";" + expected.replace('.', ',') + "\n"));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertTrue(sent.getValue().getText().contains(expected + "/20"));
        verify(sms).send(eq("+261 34 00 000 02"), contains(expected + "/20"));
    }

    @Test
    void ungradedSubmissionsRemainUnchanged() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>(Map.of(
                "submission_id", 2L, "deadline", Date.valueOf("2026-11-10"),
                "submitted_at", Timestamp.valueOf("2026-11-11 02:00:00")));
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of(row));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
        GradeReport report = new GradeReport();
        ReflectionTestUtils.setField(report, "jdbcTemplate", jdbc);
        Map<String, Object> transcript = report.build(2L);
        Map<?, ?> line = (Map<?, ?>) ((List<?>) transcript.get("exercises")).get(0);
        assertNull(line.get("grade"));
        assertNull(transcript.get("average"));
        CsvExport export = new CsvExport();
        ReflectionTestUtils.setField(export, "jdbcTemplate", jdbc);
        assertTrue(export.export().getBody().endsWith(";non;\n"));
    }
}
