package ir.piana.dev.common.handlers;

import ir.piana.dev.common.handler.ChainStep;
import ir.piana.dev.common.handler.Handler;
import ir.piana.dev.common.handler.HandlerContext;
import ir.piana.dev.common.handler.ResultDto;
import ir.piana.dev.common.participants.TransformParticipant;
import ir.piana.dev.jsonparser.json.JsonParser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@Handler
public class RegisterMerchantHandler {
    @Autowired
    private JsonParser jsonParser;

    @Autowired
    private TransformParticipant transformParticipant;

    @Value("classpath:real-to-ap.yml")
    private Resource realResource;

    @Value("classpath:legal-to-ap.yml")
    private Resource legalResource;

    @ChainStep(order = 1)
//    @Transactional(propagation = Propagation.REQUIRED)
    public void step1(HandlerContext<Request> context) {
        System.out.println(context.request().getJsonTarget().asString("message"));
    }

    @ChainStep(order = 2)
    public void step2(HandlerContext<Request> context) {
        context.addResultDto(new ResultDto(new Response(1, context.request().getDto().message)));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        private String message;

        public Request(String message) {
            this.message = message;
        }

    }

    @Getter
    @Setter
    public static class Response {
        private int id;
        private String message;

        public Response(int id, String message) {
            this.id = id;
            this.message = message;
        }
    }
}
