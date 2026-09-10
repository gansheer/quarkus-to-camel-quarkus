package org.acme.orders;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class FileIngestionTest {

    static final Path INCOMING = Path.of("target/test-orders/incoming");
    static final Path DONE = Path.of("target/test-orders/done");

    @BeforeEach
    public void setUp() throws IOException {
        Files.createDirectories(INCOMING);
    }

    @Test
    public void testCsvFileIngestion() throws IOException {
        Files.writeString(INCOMING.resolve("test-orders.csv"),
                """
                id,customer,type,amount
                ORD-100,Acme Corp,priority,1500.00
                ORD-101,Wayne Enterprises,standard,250.00
                """);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/api/orders")
                        .then()
                        .statusCode(200)
                        .body("$", hasSize(2)));

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Files.exists(DONE.resolve("test-orders.csv")));
    }
}
