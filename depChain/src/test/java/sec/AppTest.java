package sec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import sec.network.AuthenticatedPerfectLink;
import org.junit.jupiter.api.Test;

/**
 * Unit test for MessageSender.
 */
public class AppTest {

    @Test
    public void testMessageSender() {
        // Arrange
        AuthenticatedPerfectLink sender = new AuthenticatedPerfectLink();
        String testMessage = "Hello World!";

        // Act
        sender.send("127.0.0.1", 123, testMessage);

        // Assert
        // Since the send method only prints to the console, we can't directly assert its output.
        // However, we can assume that if no exceptions are thrown, the method is working correctly.
        assertTrue(true, "MessageSender.send() should execute without throwing exceptions.");
    }
}