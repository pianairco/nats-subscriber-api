package ir.piana.dev.common.nats;

import io.nats.client.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import ir.piana.dev.common.handler.*;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Configuration
public class NatsAutoConfiguration {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Bean
    @Profile("nats-server")
    public Connection natsConnection(NatsConfig natsConfig)
            throws IOException, InterruptedException, GeneralSecurityException {
        Connection nc = null;
        String serverProp = (natsConfig != null) ? natsConfig.getServer() : null;


        if (natsConfig == null || ((serverProp == null || serverProp.isEmpty()) && Objects.isNull(natsConfig.serverUrl))) {
            return null;
        }
        natsConfig.setServer(natsConfig.serverUrl);

        try {
            logger.info("autoconnecting to NATS with properties - " + natsConfig);
            logger.info("nat server = " + natsConfig.serverUrl);
            Options.Builder builder = natsConfig.toOptionsBuilder();

            builder = builder.connectionListener(new ConnectionListener() {
                public void connectionEvent(Connection conn, Events type) {
                    logger.info("NATS connection status changed " + type);
                }
            });

            builder = builder.errorListener(new ErrorListener() {
                @Override
                public void slowConsumerDetected(Connection conn, Consumer consumer) {
                    logger.info("NATS connection slow consumer detected");
                }

                @Override
                public void exceptionOccurred(Connection conn, Exception exp) {
                    logger.info("NATS connection exception occurred", exp);
                }

                @Override
                public void errorOccurred(Connection conn, String error) {
                    logger.info("NATS connection error occurred " + error);
                }
            });

            nc = Nats.connect(builder.build());
            logger.info("connecting to nats successfully => " + natsConfig.serverUrl);
        } catch (Exception e) {
            logger.error("error connecting to nats", e);
            throw e;
        }
        return nc;
    }

    @Bean
    @Profile("nats-server")
    Map<String, Class> natsHandlerClassMap(NatsRouter routerItems) throws ClassNotFoundException {
        Map<String, Class> map = new LinkedHashMap<>();
        for (NatsRouteItem item : routerItems.items) {
            if (item.response == null)
                map.put(item.handlerClass, Class.forName(item.handlerClass));
        }
        return map;
    }

    @Bean
    @Profile("nats-server")
    Map<String, Class> natsDtoClassMap(NatsRouter routerItems) throws ClassNotFoundException {
        Map<String, Class> map = new LinkedHashMap<>();

        for (NatsRouteItem item : routerItems.items) {
            if (item.dtoType != null && item.response == null) {
                map.put(item.dtoType, Class.forName(item.dtoType));
            }
        }
        return map;
    }

    @Bean
    @Profile("nats-server")
    Dispatcher natsDispatcher(Connection connection) {
        return connection.createDispatcher(message -> {
        });
    }

    @Bean
    @Profile("nats-server")
    NatsHandlers natsHandlers(Dispatcher natsDispatcher, NatsRouter natsRouter,
                              HandlerManager handlerManager,
                              @Qualifier("natsHandlerClassMap") Map<String, Class> natsHandlerClassMap,
                              @Qualifier("natsDtoClassMap") Map<String, Class> natsDtoClassMap) {
        for (NatsRouteItem item : natsRouter.items) {
            if (item.response != null) {
                natsDispatcher.subscribe(
                        item.subject,
                        Optional.ofNullable(item.group).orElse(item.subject.concat(".group")),
                        message -> {
                            message.getConnection().publish(message.getReplyTo(), item.response.getBytes());
                        });
            } else {
                natsDispatcher.subscribe(
                        item.subject,
                        Optional.ofNullable(item.group).orElse(item.subject.concat(".group")),
                        message -> {
                            try {
                                if (item.dtoType != null && message.getData().length == 0) {
                                    /*throw new HandlerRuntimeException(
                                            BaseHandlerContext.fromResult(new ResultDto(new DetailedError(
                                                    -1, "request body required",
                                                    DetailedError.ErrorTypes.BAD_REQUEST))));*/
                                }
                                /**
                                 * ToDo: body must be Object not Array
                                 */
                                handle(item, handlerManager, natsHandlerClassMap,
                                        message,
                                        RequestDtoBuilder.fromJson(
                                                Buffer.buffer().appendBytes(message.getData()).toJsonObject(),
                                                natsDtoClassMap.get(item.dtoType)).build());
                            } catch (Exception exception) {
                                logger.error(exception.getMessage());
                                error(message, exception);
                            }
                        });
            }
        }
        return new NatsHandlers();
    }

    private void handle(
            NatsRouteItem item,
            HandlerManager handlerManager,
            Map<String, Class> handlerClassMap,
            Message message,
            RequestDto requestDto) {
        try {
            DeferredResult<HandlerContext<?>> deferredResult = handlerManager.execute(
                    handlerClassMap.get(item.handlerClass), message.getReplyTo(), requestDto);

            deferredResult.setResultHandler(result -> {
                HandlerContext handlerContext = (HandlerContext) result;
                ResultDto resultDto = handlerContext.resultDto();
                if (resultDto.isSuccess()) {
                    ok(message, handlerContext, item);
                } else {
                    error(message, handlerContext);
                }
            });

            deferredResult.onError(throwable -> {
                error(message, throwable);
            });
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage());
            error(message, throwable);
        }
    }

    private void ok(Message message, HandlerContext context, NatsRouteItem item) {
        if (context.responded()) {
            logger.error("Already sent response!");
            return;
        }

        var json = JsonObject.mapFrom(context.resultDto());
        message.getConnection().publish(message.getReplyTo(), json.toBuffer().getBytes());
    }

    private void error(Message message, HandlerContext context) {
        if (context == null)
            message.getConnection().publish(message.getReplyTo(), JsonObject.mapFrom(
                            new DetailedError(-1, "unhandled error!", DetailedError.ErrorTypes.UNKNOWN))
                    .toBuffer().getBytes());
        else {
            if (context.responded()) {
                logger.error("Already sent response!");
                return;
            }
            message.getConnection().publish(message.getReplyTo(),
                    JsonObject.mapFrom(context.resultDto()).toBuffer().getBytes());
        }
    }

    private void error(Message message, Throwable throwable) {
        if (throwable instanceof HandlerRuntimeException) {
            HandlerContext context = ((HandlerRuntimeException) throwable).getContext();
            if (context.responded()) {
                logger.error("Already sent response!");
                return;
            }
            message.getConnection().publish(message.getReplyTo(),
                    JsonObject.mapFrom(context.resultDto()).toBuffer().getBytes());
        } else {
            message.getConnection().publish(message.getReplyTo(), JsonObject.mapFrom(
                            new DetailedError(-1, "unhandled error!", DetailedError.ErrorTypes.UNKNOWN))
                    .toBuffer().getBytes());
        }
    }

    static class NatsHandlers {

    }

    @Setter
    @Component
    @ConfigurationProperties(prefix = "ir.piana.dev.common.nats")
    static class NatsConfig extends NatsConnectionProps {
        String serverUrl;
    }

    @Setter
    @Component
    @ConfigurationProperties(prefix = "nats.router")
    static class NatsRouter {
        List<NatsRouteItem> items;
    }

    @Setter
    static class NatsRouteItem {
        private String subject;
        private String group;
        private String handlerClass;
        private List<String> roles;
        private String dtoType;
        private String response;
    }
}
