package org.acme.orders;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkus.runtime.annotations.RegisterForReflection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("orderRepository")
@RegisterForReflection
public class OrderRepository {

    private final List<Order> orders = new CopyOnWriteArrayList<>();

    public void add(Order order) {
        orders.add(order);
    }

    public List<Order> listAll() {
        return List.copyOf(orders);
    }
}
