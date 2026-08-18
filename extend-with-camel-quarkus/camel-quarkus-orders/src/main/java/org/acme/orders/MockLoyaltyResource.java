package org.acme.orders;

import io.quarkus.logging.Log;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/loyalty")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MockLoyaltyResource {

    @POST
    @Path("/notify")
    public Response notify(String orderJson) {
        Log.infof("Loyalty system notified: %s", orderJson);
        return Response.ok("{\"status\":\"points_awarded\"}").build();
    }
}
