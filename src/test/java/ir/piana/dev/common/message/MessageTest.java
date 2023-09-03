package ir.piana.dev.common.message;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.vertx.core.buffer.Buffer;
import ir.piana.dev.jsonparser.json.JsonParser;
import ir.piana.dev.jsonparser.json.JsonTarget;
import ir.piana.dev.yaml.bundle.YamlReloadableResourceBundleMessageSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

@SpringBootTest()
@Import(value = {MessageTest.TestConfig.class})
//@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class MessageTest {

    @Configuration
    @ComponentScan("ir.piana.dev")
    static class TestConfig {
        @Bean("messageSource")
        public MessageSource validatorMessageSource() {
            YamlReloadableResourceBundleMessageSource messageSource
                    = new YamlReloadableResourceBundleMessageSource();
            messageSource.setBasename("classpath:messages/messages");
            messageSource.setDefaultEncoding("UTF-8");
            return messageSource;
        }
    }

    @Autowired
    @Qualifier("default")
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


        CompletableFuture<Message> response = connection.request("api.test.post",
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
