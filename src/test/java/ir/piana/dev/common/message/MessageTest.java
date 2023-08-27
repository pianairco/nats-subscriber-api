package ir.piana.dev.common.message;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.vertx.core.buffer.Buffer;
import ir.piana.dev.common.context.PropertyOverrideContextInitializer;
import ir.piana.dev.jsonparser.json.JsonParser;
import ir.piana.dev.jsonparser.json.JsonTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

@SpringBootTest
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
@Import(value = {MessageTest.TestConfig.class})
public class MessageTest {

    @Configuration
    @ComponentScan("ir.piana.dev")
    static class TestConfig {

    }

    @Autowired
    private Connection connection;

    @Autowired
    private JsonParser jsonParser;

    @Test
    void PostHandlerTest(@Value("classpath:post-test.json") Resource resource) throws InterruptedException {
        byte[] contentAsByteArray = null;
        try {
            contentAsByteArray = resource.getContentAsString(Charset.forName("utf-8")).getBytes("utf-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JsonTarget request = jsonParser.fromBytes(contentAsByteArray,
                null, true);


        CompletableFuture<Message> response = connection.request("api.register-merchant",
                request.getBytes(false, "utf-8"));
        response.whenComplete((message, throwable) -> {
            if (throwable == null) {
                System.out.println(Buffer.buffer(message.getData()).toString());
            } else {
                throwable.printStackTrace();
            }
        });

        response.join();
    }
}