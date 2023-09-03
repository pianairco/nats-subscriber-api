package ir.piana.dev.common.handlers;

import ir.piana.dev.common.handler.*;
import ir.piana.dev.common.util.HandlerInterStateTransporter;
import org.springframework.beans.factory.annotation.Autowired;

@Handler
public class GetHandler extends BaseRequestHandler {
    protected GetHandler(ContextLoggerProvider contextLoggerProvider, HandlerRuntimeExceptionThrower handlerExceptionThrower) {
        super(contextLoggerProvider, handlerExceptionThrower);
    }

    @ChainStep(order = 1)
    public void step1(HandlerRequest <PostHandler.Request> handlerRequest,
                      HandlerInterStateTransporter transporter) {
        System.out.println();
    }

    @Autowired
    private HandlerResponseBuilder handlerResponseBuilder;

    @Override
    public HandlerResponse provideResponse(
            HandlerRequest handlerRequest, HandlerInterStateTransporter transporter) {
        return handlerResponseBuilder.fromDto(new PostHandler.Response(
                1, "hello get!"));
    }
}
