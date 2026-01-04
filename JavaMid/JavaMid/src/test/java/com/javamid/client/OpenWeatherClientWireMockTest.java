package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class OpenWeatherClientWireMockTest {

    static WireMockServer wm;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(0);
        wm.start();
        configureFor("localhost", wm.port());
    }

    @AfterAll
    static void stop() {
        if (wm != null) wm.stop();
    }

    @Test
    void testGetWeatherUsingWireMock() {
        String sampleJson = "{\n" +
            "  \"coord\": {\"lon\": -3.7, \"lat\": 40.42},\n" +
            "  \"weather\": [{\"main\":\"Clear\",\"description\":\"clear sky\"}],\n" +
            "  \"main\": {\"temp\": 20.5, \"humidity\": 30},\n" +
            "  \"wind\": {\"speed\": 3.6},\n" +
            "  \"name\":\"Madrid\"\n" +
            "}";

        stubFor(get(urlPathEqualTo("/data/2.5/weather"))
            .withQueryParam("q", equalTo("Madrid"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(sampleJson)));

        String baseUrl = "http://localhost:" + wm.port() + "/data/2.5/weather";
        OpenWeatherApiClient client = new OpenWeatherApiClient("test-key", baseUrl);

        JsonNode node = client.getWeatherData("Madrid");
        Assertions.assertEquals("Madrid", node.path("name").asText());
        Assertions.assertEquals(20.5, node.path("main").path("temp").asDouble());
    }
}

