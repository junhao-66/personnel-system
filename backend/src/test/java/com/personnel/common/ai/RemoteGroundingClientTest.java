package com.personnel.common.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.config.AiProperties;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class RemoteGroundingClientTest {
    AiProperties properties;HttpClient http;ObjectMapper json;RemoteGroundingClient client;
    GroundedAiContext context=new GroundedAiContext("ASSESSMENT",List.of(new GroundedAiContext.Evidence("result","FACT","已保存的偏好信息","冻结报告")));
    @BeforeEach void setup(){properties=new AiProperties();properties.setApiKey("synthetic-key");properties.setModel("configured-model");http=mock(HttpClient.class);json=new ObjectMapper();client=new RemoteGroundingClient(properties,json,http);}
    @SuppressWarnings("unchecked") void respond(int status,byte[] bytes){HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(bytes);when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));}
    byte[] response(String plan)throws Exception{return json.writeValueAsBytes(Map.of("status","completed","output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",plan))))));}
    @Test void requestUsesResponsesShapeStoreFalseAndStructuredSelectionOnly()throws Exception{
        respond(200,response("{\"items\":[{\"evidenceId\":\"result\",\"frame\":\"SUMMARY\"}]}"));assertThat(client.plan("固定指令",context)).containsExactly(new GroundedAiContext.Selection("result","SUMMARY"));
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(http).sendAsync(capture.capture(),any(HttpResponse.BodyHandler.class));var request=capture.getValue();
        assertThat(request.uri().toString()).endsWith("/v1/responses");assertThat(request.headers().firstValue("Authorization")).contains("Bearer synthetic-key");
        var body=new StringBuilder();var finished=new CompletableFuture<Void>();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}public void onNext(ByteBuffer b){body.append(StandardCharsets.UTF_8.decode(b));}public void onError(Throwable e){finished.completeExceptionally(e);}public void onComplete(){finished.complete(null);}});finished.get();
        var tree=json.readTree(body.toString());assertThat(tree.path("store").asBoolean()).isFalse();assertThat(tree.path("model").asText()).isEqualTo("configured-model");assertThat(tree.path("text").path("format").path("strict").asBoolean()).isTrue();assertThat(tree.path("input").asText()).doesNotContain("question","userId","phone");
    }
    @Test void unconfiguredOrInsecureEndpointNeverSendsRequest(){properties.setEndpoint("http://localhost:9999");assertThat(client.configured()).isFalse();assertThatThrownBy(()->client.plan("instruction",context)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(http);}
    @Test void quotaHttpErrorsInvalidJsonAndUnexpectedFreeTextAreRejected()throws Exception{
        respond(429,"quota".getBytes(StandardCharsets.UTF_8));assertThatThrownBy(()->client.plan("instruction",context)).isInstanceOf(IllegalStateException.class);
        respond(200,response("{\"items\":[{\"evidenceId\":\"result\",\"frame\":\"SUMMARY\",\"text\":\"无依据的新事实\"}]}"));assertThatThrownBy(()->client.plan("instruction",context)).isInstanceOf(IllegalStateException.class);
        respond(200,response("not json"));assertThatThrownBy(()->client.plan("instruction",context)).isInstanceOf(Exception.class);
    }
    @Test @SuppressWarnings("unchecked") void totalDeadlineCancelsSlowResponse(){properties.setTimeoutSeconds(1);var pending=new CompletableFuture<HttpResponse<byte[]>>();when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(pending);assertThatThrownBy(()->client.plan("instruction",context)).isInstanceOf(TimeoutException.class);assertThat(pending.isCancelled()).isTrue();}
    @Test void responseSubscriberCancelsOversizedBodies(){var body=new RemoteGroundingClient.LimitedBody();var subscription=mock(Flow.Subscription.class);body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[65537])));assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();verify(subscription).cancel();}
}
