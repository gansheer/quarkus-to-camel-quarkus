package org.acme.orders;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.csv.CsvDataFormat;

@ApplicationScoped
public class FileProcessingRoute extends RouteBuilder {

    @Override
    public void configure() {
        CsvDataFormat csv = new CsvDataFormat();
        csv.setSkipHeaderRecord(true);

        from("file:{{file.orders.incoming:orders/incoming}}?include=.*\\.csv&move=../done&moveFailed=../failed&readLock=changed")
                .routeId("file-ingestion")
                .log("Processing file: ${header.CamelFileName}")
                .unmarshal(csv)
                .split(body())
                    .bean("orderConverter")
                    .bean("orderRepository", "add")
                .end()
                .log("File processed: ${header.CamelFileName}");
    }
}
