package hei.school;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Development SMS provider: logs messages instead of contacting a real service. */
@Component
public class LoggingSmsSender implements SmsSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingSmsSender.class);
    private final boolean simulateFailure;

    public LoggingSmsSender(@Value("${kilasy.sms.simulate-failure:false}") boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    @Override
    public void send(String phoneNumber, String message) {
        if (simulateFailure) {
            throw new IllegalStateException("Simulated SMS provider outage");
        }
        LOGGER.info("[SMS] {} : {}", phoneNumber, message);
    }
}
