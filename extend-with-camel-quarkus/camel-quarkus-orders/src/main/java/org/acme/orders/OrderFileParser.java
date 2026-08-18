package org.acme.orders;

import java.math.BigDecimal;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("orderFileParser")
public class OrderFileParser {

    public List<Order> parse(String csvContent) {
        return csvContent.lines()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(this::parseLine)
                .toList();
    }

    private Order parseLine(String line) {
        String[] parts = line.split(",", 4);
        return new Order(
                parts[0].trim(),
                parts[1].trim(),
                parts[2].trim(),
                new BigDecimal(parts[3].trim()));
    }
}
