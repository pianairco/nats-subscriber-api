package ir.piana.dev.common.context;


import ir.piana.dev.common.jms.JmsServerItem;
import ir.piana.dev.common.nats.NatsConnectionProps;
import ir.piana.dev.common.nats.NatsJmsServerProvider;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "ir.piana.dev.common.test.jms-server")
@Setter
public class NatsJmsServerProviderImpl implements NatsJmsServerProvider {
    private List<JmsServerItem<NatsConnectionProps>> items;

    @Override
    public List<JmsServerItem<NatsConnectionProps>> jmsServers() {
        return items;
    }
}
