package ir.piana.dev.common.nats;

import io.nats.client.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import ir.piana.dev.common.handler.*;
import ir.piana.dev.common.jms.*;
import ir.piana.dev.common.util.MapStrings;
import ir.piana.dev.jsonparser.json.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class NatsAutoConfiguration {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Bean
    @Profile("nats-jms-server")
    Map<String, Class> natsHandlerClassMap(List<NatsJmsRouterProvider> routerProviders) throws ClassNotFoundException {
        Map<String, Class> map = new LinkedHashMap<>();

        if (routerProviders == null)
            return map;
        for (JmsRouterProvider provider : routerProviders) {
            if (provider.jmsRouters() == null)
                continue;
            for (JmsRouterItem router : provider.jmsRouters()) {
                for (JmsRouteItem item : router.getRoutes()) {
                    if (item.getResponse() == null && !map.containsKey(item.getHandlerClass()))
                        map.put(item.getHandlerClass(), Class.forName(item.getHandlerClass()));
                }
            }
        }
        return map;
    }

    @Bean
    @Profile("nats-jms-server")
    Map<String, Class> natsDtoClassMap(List<NatsJmsRouterProvider> routerProviders) throws ClassNotFoundException {
        Map<String, Class> map = new LinkedHashMap<>();

        if (routerProviders == null)
            return map;
        for (NatsJmsRouterProvider provider : routerProviders) {
            if (provider.jmsRouters() == null)
                continue;
            for (JmsRouterItem router : provider.jmsRouters()) {
                for (JmsRouteItem item : router.getRoutes()) {
                    if (item.getDtoType() != null &&
                            item.getResponse() == null &&
                            !map.containsKey(item.getDtoType())) {
                        map.put(item.getDtoType(), Class.forName(item.getDtoType()));
                    }
                }
            }
        }
        return map;
    }

    @Bean
    @Profile("nats-jms-server")
    Map<String, Connection> jmsConnectionMap(
            List<NatsJmsServerProvider> serverProviders) {
        Map<String, Connection> jmsServerMap = new LinkedHashMap<>();
        Map<String, JmsServerItem> serverConfMap = new LinkedHashMap<>();
        if (serverProviders == null)
            return jmsServerMap;
        for (JmsServerProvider<NatsConnectionProps> provider : serverProviders) {
            if (provider.jmsServers() == null)
                continue;
            for (JmsServerItem<NatsConnectionProps> server : provider.jmsServers()) {
                HttpServer httpServer = null;
                if (!jmsServerMap.containsKey(server.getName()) && serverConfMap.entrySet().stream().noneMatch(entry ->
                        (server.getServerUrl()).equals(
                                entry.getValue().getServerUrl()))) {
                    try {
                        logger.info("autoconnecting to NATS with properties - " + server.getSpecificParams());
                        logger.info("nat server = " + server.getSpecificParams().getServer());
                        Options.Builder builder = server.getSpecificParams().toOptionsBuilder();

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

                        Connection nc = Nats.connect(builder.build());
                        jmsServerMap.put(server.getName(), nc);
                        logger.info("connecting to nats successfully => " + server.getSpecificParams().getServer());
                    } catch (Exception e) {
                        logger.error("error connecting to nats", e);
                        throw new RuntimeException(e);
                    }
                    /*jmsServerMap.put(server.getName(), vertx.createHttpServer(new HttpServerOptions()
                            .setHost(server.getHost())
                            .setPort(server.getPort())
                            .setIdleTimeout(server.getIdleTimeout())
                            .setReusePort(Boolean.TRUE)
                            .setTcpQuickAck(Boolean.TRUE)
                            .setTcpCork(Boolean.TRUE)
                            .setTcpFastOpen(Boolean.TRUE)));
                    httpServer = httpServerMap.get(server.getName());*/
                }
                /*Router vertxRouter = routerMap.get(server.getName());

                if (vertxRouter != null) {
                    var cause = httpServer.requestHandler(vertxRouter).listen().cause();
                    if (cause != null)
                        throw new RuntimeException(cause.getMessage());
                }*/
                logger.info("Successfully started HTTP server and listening on {}",
                        server.getServerUrl());
            }
        }

        return jmsServerMap;
    }

    @Bean
    @Profile("nats-jms-server")
    NatsConnectionFake connection(
            Map<String, Connection> connectionMap,
            AnnotationConfigApplicationContext applicationContext,
            ConfigurableBeanFactory beanFactory) {
        if (connectionMap.isEmpty()) {
            logger.info("there is no nats connection!");
            return null;
        }
        connectionMap.forEach((key, value) -> applicationContext.registerBean(key, Connection.class, () -> value));

        return new NatsConnectionFake();
    }

    @Bean
    @Profile("nats-jms-server")
    Map<String, Dispatcher> natsDispatcherMap(Map<String, Connection> connectionMap) {
        return connectionMap.entrySet().stream().map(entry ->
                new AbstractMap.SimpleEntry<String, Dispatcher>(entry.getKey(), entry.getValue()
                        .createDispatcher(message -> {
                        }))
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Bean
    @Profile("nats-jms-server")
    NatsHandlersFake natsHandlers(Map<String, Dispatcher> dispatcherMap,
                                  List<NatsJmsRouterProvider> routerProviders,
                                  HandlerRequestBuilder handlerRequestBuilder,
                                  MessageSource messageSource,
                                  HandlerManager handlerManager,
                                  JsonParser jsonParser,
                                  @Qualifier("natsHandlerClassMap") Map<String, Class> natsHandlerClassMap,
                                  @Qualifier("natsDtoClassMap") Map<String, Class> natsDtoClassMap) {
        for (NatsJmsRouterProvider routerProvider : routerProviders) {
            for (JmsRouterItem routerItem : routerProvider.jmsRouters()) {
                for (JmsRouteItem item : routerItem.getRoutes()) {
                    if (item.getResponse() != null) {
                        dispatcherMap.get(routerItem.getServerName()).subscribe(
                                item.getSubject(),
                                Optional.ofNullable(item.getGroup()).orElse(item.getSubject().concat(".group")),
                                message -> {
                                    message.getConnection().publish(message.getReplyTo(), item.getResponse().getBytes());
                                });
                    } else {
                        dispatcherMap.get(routerItem.getServerName()).subscribe(
                                item.getSubject(),
                                Optional.ofNullable(item.getGroup()).orElse(item.getSubject().concat(".group")),
                                message -> {
                                    try {
                                        if (item.getDtoType() != null && message.getData().length == 0) {
                                            throw new RuntimeException("should be have body!");
                                        }
                                        /***
                                         * ToDo: body must be Object not Array
                                         */
                                        handle(item, handlerManager, messageSource, natsHandlerClassMap,
                                                message,
                                                handlerRequestBuilder.fromBuffer(Buffer.buffer().appendBytes(message.getData()),
                                                                natsDtoClassMap.get(item.getDtoType()),
                                                                MapStrings.toConsume().build(),
                                                                ""));
                                    } catch (Exception exception) {
                                        logger.error(exception.getMessage());
                                        error(message, messageSource, exception);
                                    }
                                });
                    }
                }
            }
        }

        return new NatsHandlersFake();
    }

    private void handle(
            JmsRouteItem item,
            HandlerManager handlerManager,
            MessageSource messageSource,
            Map<String, Class> handlerClassMap,
            Message message,
            HandlerRequest handlerRequest) {
        try {
            DeferredResult<HandlerResponse> deferredResult = handlerManager.execute(
                    handlerClassMap.get(item.getHandlerClass()), handlerRequest);

            deferredResult.setResultHandler(result -> {
                HandlerResponse handlerContext = (HandlerResponse) result;
                ok(message, handlerContext, item);
            });

            deferredResult.onError(throwable -> {
                error(message, messageSource, throwable);
            });
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage());
            error(message, messageSource, throwable);
        }
    }

    private void ok(Message message, HandlerResponse response, JmsRouteItem item) {
        message.getConnection().publish(message.getReplyTo(), response.getBuffer().getBytes());
    }

    private void error(Message message, MessageSource messageSource, Throwable throwable) {
        HandlerDetailedError.ThrowableError throwableError = null;
        int httpStatus = 500;
        if (throwable instanceof HandlerRuntimeException) {
            HandlerDetailedError detailedError = ((HandlerRuntimeException) throwable).getDetailedError();
            if (((HandlerRuntimeException) throwable).isResponded()) {
                logger.error("Already sent response!");
                return;
            }
            try {
                throwableError = detailedError.toThrowableError(messageSource);
            } catch (Exception e) {
                throwableError = HandlerErrorType.UNKNOWN.generateDetailedError(
                                "error occurred on translate message!")
                        .toThrowableError(messageSource);
            }
        } else {
            throwableError = HandlerErrorType.UNKNOWN.generateDetailedError("unknown error!")
                    .toThrowableError(messageSource);
        }

        message.getConnection().publish(message.getReplyTo(), JsonObject.mapFrom(throwableError).toBuffer().getBytes());
    }

    private static class NatsHandlersFake {

    }

    private static class NatsConnectionFake {

    }
}
