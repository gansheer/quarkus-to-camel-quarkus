package org.acme.orders;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class OrderResourceTest {

    @Test
    public void testCreateAndListOrders() {
        given()
                .contentType("application/json")
                .body("""
                        {"id":"ORD-001","customer":"Acme Corp","type":"priority","amount":1500.00}
                        """)
                .when().post("/api/orders")
                .then()
                .statusCode(201)
                .body("id", is("ORD-001"))
                .body("customer", is("Acme Corp"));

        given()
                .when().get("/api/orders")
                .then()
                .statusCode(200)
                .body("id", hasItem("ORD-001"));
    }
}
