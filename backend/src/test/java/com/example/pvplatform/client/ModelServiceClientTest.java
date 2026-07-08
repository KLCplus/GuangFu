package com.example.pvplatform.client;

import com.example.pvplatform.client.dto.ModelPredictRequest;
import com.example.pvplatform.client.vo.ModelPredictResponse;
import com.example.pvplatform.common.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ModelServiceClientTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HttpServer server;
    private ModelServiceClient modelServiceClient;
    private ModelServiceHealthClient healthClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", exchange ->
            send(exchange, 200, "{\"status\":\"ok\",\"service\":\"model-service\"}"));
        server.createContext("/model-api/models", exchange -> send(exchange, 200, """
            {
              "code": 200,
              "message": "success",
              "data": [
                {"modelName": "DLinear", "modelType": "NUMERIC", "description": "DLinear"},
                {"modelName": "iTransformer", "modelType": "NUMERIC", "description": "iTransformer"},
                {"modelName": "CNN_LSTM", "modelType": "FUSION", "description": "CNN-LSTM"}
              ]
            }
            """));
        server.createContext("/model-api/predict", this::handlePredict);
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        modelServiceClient = new ModelServiceClient(webClient);
        healthClient = new ModelServiceHealthClient(webClient);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReadHealthAndModelList() {
        assertTrue(healthClient.isHealthy());
        assertTrue(healthClient.hasModel("iTransformer"));
        assertFalse(healthClient.hasModel("lstm_v1"));
    }

    @Test
    void shouldCallPredictAndValidateResponse() {
        ModelPredictResponse response = modelServiceClient.predict(
            new ModelPredictRequest("iTransformer", numericFrames()),
            "iTransformer",
            6);

        assertEquals(200, response.code());
        assertEquals("iTransformer", response.data().modelName());
        assertEquals(6, response.data().predictions().size());
        assertEquals(5, response.data().predictions().getFirst().timeOffset());
        assertEquals(123L, response.data().costTime());
    }

    @Test
    void shouldMapModelServiceValidationErrorToBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
            modelServiceClient.predict(
                new ModelPredictRequest("Unknown", numericFrames()),
                "Unknown",
                6));

        assertEquals(400, ex.getCode());
    }

    private void handlePredict(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("\"Unknown\"")) {
            send(exchange, 400, "{\"detail\":\"Unknown model\"}");
            return;
        }
        send(exchange, 200, """
            {
              "code": 200,
              "message": "success",
              "data": {
                "modelName": "iTransformer",
                "predictions": [
                  {"timeOffset": 5, "predictPower": 510.0},
                  {"timeOffset": 10, "predictPower": 515.0},
                  {"timeOffset": 15, "predictPower": 520.0},
                  {"timeOffset": 20, "predictPower": 525.0},
                  {"timeOffset": 25, "predictPower": 530.0},
                  {"timeOffset": 30, "predictPower": 535.0}
                ],
                "costTime": 123
              }
            }
            """);
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

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
