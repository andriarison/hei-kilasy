package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Shared grade calculation. The entire deadline day is allowed.
 * An exercise without a deadline (timed only, C3) has no late penalty.
 */
public final class GradeCalculator {
    private GradeCalculator() {}

    public static BigDecimal calculate(BigDecimal rawPoints, BigDecimal maxPoints,
                                       LocalDate deadline, LocalDateTime submittedAt) {
        long lateDays = deadline == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate()));
        BigDecimal multiplier = BigDecimal.ONE.subtract(
                new BigDecimal("0.10").multiply(BigDecimal.valueOf(Math.min(lateDays, 5))));
        return rawPoints.max(BigDecimal.ZERO).multiply(new BigDecimal("20"))
                .multiply(multiplier).divide(maxPoints, 1, RoundingMode.HALF_UP);
    }
}
