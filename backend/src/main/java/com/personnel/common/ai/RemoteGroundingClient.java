package com.personnel.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.config.AiProperties;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** A bounded Responses adapter. Model chooses evidence and framing; it cannot add display text. */
@Component
public class RemoteGroundingClient {

    private final AiProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public RemoteGroundingClient(AiProperties properties, ObjectMapper json) {
        this(
            properties,
            json,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        );
    }

    public RemoteGroundingClient(AiProperties properties, ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.http = http;
    }

    public boolean configured() {
        try {
            var uri = URI.create(properties.getEndpoint());
            return (
                "https".equals(uri.getScheme()) &&
                uri.getHost() != null &&
                uri.getUserInfo() == null &&
                uri.getFragment() == null &&
                !properties.getApiKey().isBlank() &&
                !properties.getModel().isBlank()
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public List<GroundedAiContext.Selection> plan(String instruction, GroundedAiContext context)
        throws Exception {
        if (!configured()) throw new IllegalStateException("Remote not configured");
        var itemSchema = Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "evidenceId",
                Map.of(
                    "type",
                    "string",
                    "enum",
                    context.evidence().stream().map(GroundedAiContext.Evidence::id).toList()
                ),
                "frame",
                Map.of("type", "string", "enum", List.of("SUMMARY", "OBSERVE", "TRY", "REVIEW"))
            ),
            "required",
            List.of("evidenceId", "frame"),
            "additionalProperties",
            false
        );
        var schema = Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "items",
                Map.of("type", "array", "items", itemSchema, "minItems", 1, "maxItems", 6)
            ),
            "required",
            List.of("items"),
            "additionalProperties",
            false
        );
        var body = Map.of(
            "model",
            properties.getModel(),
            "store",
            false,
            "instructions",
            instruction +
            " 只选择现有evidenceId及frame：事实用SUMMARY/OBSERVE，行动建议用TRY/REVIEW。最多6条，不重复。",
            "input",
            json.writeValueAsString(context),
            "max_output_tokens",
            800,
            "text",
            Map.of(
                "format",
                Map.of(
                    "type",
                    "json_schema",
                    "name",
                    "grounded_plan",
                    "strict",
                    true,
                    "schema",
                    schema
                )
            )
        );
        var request = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .header("Authorization", "Bearer " + properties.getApiKey())
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(body),
                    StandardCharsets.UTF_8
                )
            )
            .build();
        var pending = http.sendAsync(request, info -> new LimitedBody());
        HttpResponse<byte[]> response;
        try {
            response = pending.get(
                properties.getTimeoutSeconds(),
                java.util.concurrent.TimeUnit.SECONDS
            );
        } catch (Exception exception) {
            pending.cancel(true);
            throw exception;
        }
        byte[] bytes = response.body();
        if (response.statusCode() != 200 || bytes.length > 65536) throw new IllegalStateException(
            "Remote unavailable"
        );
        var root = json.readTree(bytes);
        if (!"completed".equals(root.path("status").asText())) throw new IllegalStateException(
            "Incomplete response"
        );
        String output = null;
        for (var message : root.path("output"))
            for (var part : message.path("content"))
                if ("output_text".equals(part.path("type").asText())) {
                    if (output != null) throw new IllegalStateException("Multiple outputs");
                    output = part.path("text").asText();
                }
        if (output == null || output.length() > 12000) throw new IllegalStateException(
            "Missing output"
        );
        var plan = json.readTree(output);
        if (
            !plan.isObject() ||
            plan.size() != 1 ||
            !plan.path("items").isArray() ||
            plan.path("items").isEmpty() ||
            plan.path("items").size() > 6
        ) throw new IllegalStateException("Invalid plan");
        var selections = new ArrayList<GroundedAiContext.Selection>();
        for (var item : plan.path("items")) {
            if (
                !item.isObject() ||
                item.size() != 2 ||
                !item.path("evidenceId").isTextual() ||
                !item.path("frame").isTextual()
            ) throw new IllegalStateException("Invalid selection");
            selections.add(
                new GroundedAiContext.Selection(
                    item.path("evidenceId").asText(),
                    item.path("frame").asText()
                )
            );
        }
        return selections;
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final java.util.concurrent.CompletableFuture<byte[]> completed =
            new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;

        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return completed;
        }

        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        public void onNext(List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 65536) {
                    subscription.cancel();
                    completed.completeExceptionally(
                        new IllegalStateException("Remote body too large")
                    );
                    return;
                }
                var part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            completed.completeExceptionally(error);
        }

        public void onComplete() {
            completed.complete(bytes.toByteArray());
        }
    }
}
