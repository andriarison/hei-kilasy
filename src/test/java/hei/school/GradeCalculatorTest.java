package hei.school;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradeCalculatorTest {
    @ParameterizedTest
    @CsvSource({
        "16,20,2026-11-09T18:00:00,16.0",
        "16,20,2026-11-10T23:59:59,16.0",
        "16,20,2026-11-11T00:00:00,14.4",
        "16,20,2026-11-11T02:00:00,14.4",
        "16,20,2026-11-13T10:00:00,11.2",
        "16,20,2026-11-15T00:00:00,8.0",
        "16,20,2026-11-18T15:00:00,8.0",
        "0,20,2026-11-18T15:00:00,0.0",
        "-1,20,2026-11-18T15:00:00,0.0",
        "8,10,2026-11-11T02:00:00,14.4",
        "32,40,2026-11-13T10:00:00,11.2",
        "13.37,20,2026-11-11T02:00:00,12.0",
        "13.35,20,2026-11-10T23:00:00,13.4"
    })
    void calculatesFinalGrade(String raw, String max, String submittedAt, String expected) {
        assertEquals(new BigDecimal(expected), GradeCalculator.calculate(
                new BigDecimal(raw), new BigDecimal(max), LocalDate.of(2026, 11, 10),
                LocalDateTime.parse(submittedAt)));
    }
}
