package org.acme.orders;

import java.math.BigDecimal;

public class Order {

    private String id;
    private String customer;
    private String type;
    private BigDecimal amount;

    public Order() {
    }

    public Order(String id, String customer, String type, BigDecimal amount) {
        this.id = id;
        this.customer = customer;
        this.type = type;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Order{id='%s', customer='%s', type='%s', amount=%s}".formatted(id, customer, type, amount);
    }
}
