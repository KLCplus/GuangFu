package com.example.pvplatform.client;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_MODEL_SERVICE_IT", matches = "true")
class ModelServiceIntegrationTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void shouldCallRunningModelService() {
        String baseUrl = System.getenv().getOrDefault("MODEL_SERVICE_BASE_URL", "http://127.0.0.1:9000");
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

        ModelServiceHealthClient healthClient = new ModelServiceHealthClient(webClient);
        assertTrue(healthClient.isHealthy());
        assertTrue(healthClient.hasModel("iTransformer"));

        ModelServiceClient client = new ModelServiceClient(webClient);
        ModelPredictResponse response = client.predict(
            new ModelPredictRequest("iTransformer", numericFrames()),
            "iTransformer",
            6);

        assertEquals(200, response.code());
        assertEquals("iTransformer", response.data().modelName());
        assertEquals(6, response.data().predictions().size());
        assertEquals(List.of(5, 10, 15, 20, 25, 30),
            response.data().predictions().stream().map(ModelPredictResponse.Prediction::timeOffset).toList());
        assertTrue(response.data().costTime() >= 0);
    }

    private List<ModelPredictRequest.InputFrame> numericFrames() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 6, 10, 0);
        return IntStream.range(0, 30)
            .mapToObj(i -> new ModelPredictRequest.InputFrame(
                start.plusMinutes(i).format(FORMATTER),
                500.0 + i,
                31.2,
                820.5))
            .toList();
    }
}
