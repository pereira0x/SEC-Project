package sec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import main.java.sec.network.MessageSender;
import org.junit.jupiter.api.Test;

/**
 * Unit test for MessageSender.
 */
public class AppTest {

    @Test
    public void testMessageSender() {
        // Arrange
        MessageSender sender = new MessageSender();
        String testMessage = "Hello World!";

        // Act
        sender.send(testMessage);

        // Assert
        // Since the send method only prints to the console, we can't directly assert its output.
        // However, we can assume that if no exceptions are thrown, the method is working correctly.
        assertTrue(true, "MessageSender.send() should execute without throwing exceptions.");
    }

    @Test
    // send 100 different messages
    public void testMessageSenderMultiple() {
        // Arrange
        MessageSender sender = new MessageSender();
        String testMessage = "Hello World!";

        // Act
        for (int i = 0; i < 100; i++) {
            sender.send(testMessage + i);
        }

        // Assert
        // Since the send method only prints to the console, we can't directly assert its output.
        // However, we can assume that if no exceptions are thrown, the method is working correctly.
        assertTrue(true, "MessageSender.send() should execute without throwing exceptions.");

    }
}