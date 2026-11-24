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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(OutputCaptureExtension.class)
class SmsNotificationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final Map<String, Object> row = new HashMap<>(Map.of(
            "id", 2L, "student_id", 2L, "title", "Bubble sort",
            "deadline", Date.valueOf("2026-11-10"),
            "submitted_at", Timestamp.valueOf("2026-11-11 02:00:00"),
            "max_points", new BigDecimal("20"),
            "full_name", "Test Student", "email", "student@example.test"));

    private SubmissionController controller(SmsSender sms, String phone) {
        row.put("phone", phone);
        when(jdbc.queryForList(anyString(), anyLong())).thenReturn(List.of(row));
        // Model persisted points so a later detail read checks the saved value.
        when(jdbc.update(anyString(), any(BigDecimal.class), nullable(String.class), anyLong()))
                .thenAnswer(call -> {
                    row.put("raw_points", call.getArgument(1));
                    return 1;
                });
        SubmissionController controller = new SubmissionController();
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(controller, "mailSender", mail);
        ReflectionTestUtils.setField(controller, "smsSender", sms);
        return controller;
    }

    @Test
    void providerLogsRecipientAndMessage(CapturedOutput output) {
        new LoggingSmsSender(false).send("+261 34 00 000 02", "Your grade is 14.4/20.");
        assertTrue(output.getAll().contains("[SMS] +261 34 00 000 02 : Your grade is 14.4/20."));
    }

    @Test
    void simulatedOutageDoesNotUndoPublishedGradeOrEmail(CapturedOutput output) {
        SubmissionController controller = controller(new LoggingSmsSender(true), "+261 34 00 000 02");
        ResponseEntity<?> response = controller.grade(2L, Map.of("points", 16));
        assertEquals(200, response.getStatusCode().value());
        assertEquals(new BigDecimal("14.4"), ((Map<?, ?>) response.getBody()).get("grade"));
        assertEquals(new BigDecimal("16.00"), row.get("raw_points"));
        assertEquals(new BigDecimal("14.4"), ((Map<?, ?>) controller.get(2L).getBody()).get("grade"));
        verify(mail).send(any(SimpleMailMessage.class));
        assertTrue(output.getAll().contains("Simulated SMS provider outage"));
        assertFalse(output.getAll().contains("[SMS]"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void missingPhoneSkipsSmsAndPreservesEmail(String phone) {
        SmsSender sms = mock(SmsSender.class);
        ResponseEntity<?> response = controller(sms, phone).grade(2L, Map.of("points", 16));
        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(sms);
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void invalidGradeDoesNotNotify() {
        SmsSender sms = mock(SmsSender.class);
        ResponseEntity<?> response = controller(sms, "+261 34 00 000 02").grade(2L, Map.of("points", -1));
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(sms, mail);
        verify(jdbc, never()).update(anyString(), any(BigDecimal.class), nullable(String.class), anyLong());
    }
}
