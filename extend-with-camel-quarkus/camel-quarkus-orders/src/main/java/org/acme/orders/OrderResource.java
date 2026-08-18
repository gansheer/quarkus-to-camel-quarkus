package org.acme.orders;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.camel.ProducerTemplate;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderRepository repository;

    @Inject
    ProducerTemplate producerTemplate;

    @GET
    public List<Order> list() {
        return repository.listAll();
    }

    @POST
    public Response create(Order order) {
        repository.add(order);
        producerTemplate.sendBody("direct:order-placed", order);
        return Response.status(Response.Status.CREATED).entity(order).build();
    }
}
