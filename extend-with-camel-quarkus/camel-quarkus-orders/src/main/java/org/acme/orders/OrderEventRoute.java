package org.acme.orders;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class OrderEventRoute extends RouteBuilder {

    @Override
    public void configure() {
        onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .backOffMultiplier(2)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .log(LoggingLevel.ERROR, "Order event processing failed: ${exception.message}");

        from("direct:order-placed")
                .routeId("order-event")
                .marshal().json()
                .to("kafka:order-events")
                .log("Order event sent to Kafka")
                .setHeader("Content-Type", constant("application/json"))
                .to("http://{{loyalty.api.url}}/api/loyalty/notify")
                .log("Loyalty service notified");
    }
}
