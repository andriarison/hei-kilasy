package hei.school;

/** Provider-independent boundary for sending SMS messages. */
public interface SmsSender {
    void send(String phoneNumber, String message);
}
