package ir.piana.dev.common.context;

import ir.piana.dev.common.jms.JmsRouterItem;
import ir.piana.dev.common.nats.NatsJmsRouterProvider;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ir.piana.dev.common.test.jms-router")
@Setter
public class NatsJmsRouterProviderImpl implements NatsJmsRouterProvider {
    private List<JmsRouterItem> items;

    @Override
    public List<JmsRouterItem> jmsRouters() {
        return items;
    }
}
