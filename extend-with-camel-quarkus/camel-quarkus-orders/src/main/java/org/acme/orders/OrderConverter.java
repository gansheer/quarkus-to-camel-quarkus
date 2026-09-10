package org.acme.orders;

import java.math.BigDecimal;
import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("orderConverter")
@RegisterForReflection
public class OrderConverter {

    public Order fromCsvRow(List<String> row) {
        return new Order(
                row.get(0).trim(),
                row.get(1).trim(),
                row.get(2).trim(),
                new BigDecimal(row.get(3).trim()));
    }
}
