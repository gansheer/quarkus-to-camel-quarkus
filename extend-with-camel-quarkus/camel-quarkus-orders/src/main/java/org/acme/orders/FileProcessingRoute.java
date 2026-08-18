package org.acme.orders;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class FileProcessingRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("file:orders/incoming?include=.*\\.csv&move=../done&moveFailed=../failed&readLock=changed")
                .routeId("file-ingestion")
                .log("Processing file: ${header.CamelFileName}")
                .bean("orderFileParser")
                .split(body())
                    .bean("orderRepository", "add")
                .end()
                .log("File processed: ${header.CamelFileName}");
    }
}
