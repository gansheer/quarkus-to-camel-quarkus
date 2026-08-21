---
title: "Extending Your Quarkus Application with Apache Camel"
date: 2026-08-XX
draft: true
authors: [TODO]
categories: ["Howtos", "Camel Quarkus"]
preview: "A practical guide showing how to extend an existing Quarkus REST app with Camel for Kafka events, HTTP service calls, and file ingestion"
---

You built a standard Quarkus application, with REST endpoints, a service layer, maybe a database. Then a new requirement arises: "When an order is placed, send an event to Kafka so the analytics team can track it." A week later another one: "Also notify the partner loyalty system via their REST API and handle it gracefully when their API is down." And another one: "The partner is going to start sending us CSV order files."...

Each of these requirements is an integration task. You can build them all with what Quarkus already gives you: Reactive Messaging for Kafka, a REST client with retry logic, a scheduled job for file polling. But as the list grows, you end up writing the same boilerplate: retry loops, error
handling, message transformation, file lifecycle management.

If your impression of Apache Camel revolves around XML configuration files and standalone integration
servers, it's worth a second look. Modern Camel is a lightweight library with
[400+ connectors](https://camel.apache.org/components/next/), and with [Camel Quarkus](https://camel.apache.org/camel-quarkus/next/) it runs inside your application as naturally as any other CDI bean: same `application.properties`, same `quarkus dev`, same native compilation support.

This post starts from a working Quarkus REST application and progressively extends it with three integration capabilities using Camel. You'll see exactly what changes in your existing code (spoiler: not much) and what stays untouched.

**[Figure 1 — What you'll build]**
![What you'll build](camel-quarkus-architecture.png "Architecture diagram. Left side: the existing Quarkus REST application (OrderResource to OrderRepository). Right side: three Camel routes extending it: (1) an event route sending order data to a Kafka topic, (2) an HTTP call to the partner loyalty API with retry, (3) a file ingestion route reading CSVs from a partner directory. A ProducerTemplate arrow bridges the existing REST code to the Camel routes.")


## The starting point

Here's what you're starting with: a simple order management REST API.

```java
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderRepository repository;

    @GET
    public List<Order> list() {
        return repository.listAll();
    }

    @POST
    public Response create(Order order) {
        repository.add(order);
        return Response.status(Response.Status.CREATED).entity(order).build();
    }
}
```

`Order` is a POJO with four fields (`id`, `customer`, `type`, `amount`). `OrderRepository` is an `@ApplicationScoped` CDI bean that stores orders in memory.

Start it up and make sure it works:

```shell
mvn quarkus:dev
```

```shell
# Create an order
curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"id":"ORD-001","customer":"Acme Corp","type":"priority","amount":1500.00}'

# List orders
curl -s http://localhost:8080/api/orders
```

You should get:
```json
[
    {
        "id": "ORD-001",
        "customer": "Acme Corp",
        "type": "priority",
        "amount": 1500.0
    }
]
```

Now let's extend it.

## Adding Camel to the project

Add the Camel Quarkus extensions you'll need:

```shell
quarkus ext add camel-quarkus-kafka camel-quarkus-jackson \
    camel-quarkus-http camel-quarkus-file camel-quarkus-bean \
    camel-quarkus-direct camel-quarkus-csv
```

If you don't have the [Quarkus CLI](https://quarkus.io/guides/cli-tooling) you can add directly in your pom.xml:

* the quarkus-camel-bom to <dependencyManagement> so versions are managed:
```xml
<dependencyManagement>
...
  <dependency>
      <groupId>io.quarkus.platform</groupId>
      <artifactId>quarkus-camel-bom</artifactId>
      <version>${quarkus.platform.version}</version>
      <type>pom</type>
      <scope>import</scope>
  </dependency>
...
</dependencyManagement>
```
* the dependencies:
```xml
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-direct</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-bean</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-file</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-kafka</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-http</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-jackson</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.camel.quarkus</groupId>
      <artifactId>camel-quarkus-csv</artifactId>
  </dependency>
```

If you have Podman or Docker running, Quarkus Dev Services will start a Kafka broker automatically.

Camel routes are CDI beans that extend `RouteBuilder`. Quarkus discovers them like any other bean:

```java
@ApplicationScoped
public class MyRoutes extends RouteBuilder {
    @Override
    public void configure() {
        // routes go here
    }
}
```

At this point, nothing has changed in your existing code. Your REST endpoints, services, and beans are unaffected.

## Sending order events to Kafka

**The first requirement:** every time an order is placed, publish an event to a Kafka topic so downstream systems (analytics, warehouse, notifications) can react to it.

With SmallRye Reactive Messaging, you'd inject an `Emitter<String>`, annotate it with `@Channel("order-events")`, and add serializer, deserializer, and topic configuration for each channel in `application.properties`. It works, but it couples your REST layer directly to Kafka infrastructure and adds a fair amount of boilerplate configuration.

Using Camel, the change to your existing code is two lines:

```java
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
```

`ProducerTemplate` is Camel's bridge from your existing code into a Camel route. A *route* in Camel is a processing pipeline: it starts from an input (a URI like `direct:`, `kafka:`, or `file:`), applies a chain of steps (transformation, filtering, logging) and then sends the result to one or more outputs. Think of it as a method chain that wires together I/O and processing without the boilerplate. In Camel Quarkus, `ProducerTemplate` is automatically available as a CDI bean. You just need to `@Inject` it. The `sendBody` call pushes the order object into the `direct:order-placed` endpoint, which is the entry point of your Camel route.

Here's that Camel route you need to add:

```java
@ApplicationScoped
public class OrderEventRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:order-placed")
            .routeId("order-event")
            .marshal().json()
            .to("kafka:order-events")
            .log("Order event sent to Kafka");
    }
}
```

And the Kafka configuration to add in `application.properties`:

```properties
camel.component.kafka.brokers=${kafka.bootstrap.servers:localhost:9092}
```

The Camel route does three steps: receive the order, serialize it to JSON, send it to Kafka.


**Try it**, create an order and watch the console:

```shell
curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"id":"ORD-002","customer":"Global Inc","type":"standard","amount":250.00}'
```

In the logs:

```
... order-event ... Order event sent to Kafka
```

Your order is persisted *and* published to Kafka.

**Extensions used:** `camel-quarkus-kafka`, `camel-quarkus-jackson`, `camel-quarkus-direct`

## Notifying the loyalty system

**The next requirement:** also call the partner's loyalty API over HTTP so customers earn points when they place an order. The partner's API is unreliable, so you need to handle retries with backoff.

The order event Camel route already handles the Kafka step. Let's add the loyalty notification: two more lines in the same route, plus an error policy:

```java
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
            .log(LoggingLevel.ERROR,
                    "Order event processing failed: ${exception.message}");

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
```

The `onException` block retries any failed step up to 3 times with exponential backoff, each attempt logged at WARN level. If the loyalty API returns a 500 or the connection times out, Camel retries that step automatically. If all attempts fail, it logs the error and moves on but the order is still safely persisted, since that happened before the Camel call.

If you want to integrate another external service later, you can add one more `.to("http://...")` line.

```properties
loyalty.api.url=loyalty.example.com
```

> [!NOTE]
> The companion project includes a `MockLoyaltyResource` that simulates the partner API inside the same application. To use it locally:
> ```properties
> loyalty.api.url=localhost:${quarkus.http.port}
> ```

**Try it**, create another order and watch both integrations fire:

```shell
curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"id":"ORD-003","customer":"Stark Industries","type":"priority","amount":8900.00}'
```

In the logs:

```
... order-event ... Order event sent to Kafka
... order-event ... Loyalty service notified
```

The two integrations are triggered from a single `sendBody` call.

**Extension used:** `camel-quarkus-http`

## Ingesting files from a partner

**The third requirement:** a partner drops CSV order files into a directory. The application should parse each file, ingest the orders, and move the original to a `done` directory. The failed files go to a `failed` directory.

Building this yourself means a `@Scheduled` method polling a directory, try/catch blocks for each file, separate logic for moving successful and failed files, and protection against partially-written files being picked up too early.

Camel only requires a self-contained route:

```java
@ApplicationScoped
public class FileProcessingRoute extends RouteBuilder {

    @Override
    public void configure() {
        CsvDataFormat csv = new CsvDataFormat();
        csv.setSkipHeaderRecord(true);

        from("file:orders/incoming?include=.*\\.csv"
                + "&move=../done&moveFailed=../failed"
                + "&readLock=changed")
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
```

The `file` component handles polling, read-locking (no partial reads), and move-on-success/failure through URI options. Camel's built-in `CsvDataFormat` parses the file content into rows, skipping the header. After `split(body())`, each exchange carries one row as a `List<String>`. `orderConverter` is a small bean that maps each row to an `Order`:

```java
@ApplicationScoped
@Named("orderConverter")
public class OrderConverter {

    public Order fromCsvRow(List<String> row) {
        return new Order(
                row.get(0).trim(),
                row.get(1).trim(),
                row.get(2).trim(),
                new BigDecimal(row.get(3).trim()));
    }
}
```

`orderRepository` is your existing CDI bean, referenced by name. For Camel to look up the bean by name, add `@Named("orderRepository")` to `OrderRepository`.

**Try it**, drop a CSV file and verify:

```shell
cat > orders/incoming/orders.csv << 'EOF'
id,customer,type,amount
ORD-010,Acme Corp,priority,1500.00
ORD-011,Wayne Enterprises,standard,250.00
ORD-012,Stark Industries,priority,8900.00
EOF
```

In the logs:

```
... file-ingestion ... Processing file: orders.csv
... file-ingestion ... File processed: orders.csv
```

```shell
curl -s http://localhost:8080/api/orders
```

You'll see the orders from the CSV file alongside any you created via the REST API. The original file has been moved to `orders/done/`. A malformed file would go to `orders/failed/` instead.

**Extensions used:** `camel-quarkus-file`, `camel-quarkus-csv`, `camel-quarkus-bean`

## What changed in your existing code

Let's look into the changes. You added three integration capabilities: Kafka events, HTTP API calls with retries, and file ingestion. Your existing code changed by exactly three lines:

1. `@Inject ProducerTemplate producerTemplate` — one new field in `OrderResource`
2. `producerTemplate.sendBody("direct:order-placed", order)` — one new call in `OrderResource`
3. `@Named("orderRepository")` on `OrderRepository` — so Camel can look it up by name

Everything else is new classes (`RouteBuilder`s, `OrderConverter`) that live alongside your existing code. Nothing was rewritten. Nothing was removed. The file ingestion route didn't touch your REST layer at all.

**[Figure 2 — Data flow through the extended application]**
![Data flow through the extended application](camel-quarkus-dataflow.png "Flow diagram showing two independent paths. Path 1: a REST POST request arrives → OrderResource persists the order → ProducerTemplate sends to direct:order-placed → Camel marshals to JSON → sends to the order-events Kafka topic → calls the loyalty HTTP API (with retry/backoff on failure). Path 2: a CSV file lands in orders/incoming/ → Camel file route picks it up (with read-lock) → CsvDataFormat parses rows → orderConverter maps each row to an Order → orderRepository persists each order → file moves to orders/done/ (or orders/failed/ on error). Both paths feed into the same OrderRepository, visible through the existing GET /api/orders endpoint.")

## Camel lives alongside your existing code

A few things worth knowing about how the two coexist:

- **CDI beans are shared.** Any bean with `@Named` or `@Identifier` is available in Camel routes via `.bean("beanName")`. You can also look up beans by type with `.bean(MyService.class)`.

- **REST endpoints coexist.** Your JAX-RS endpoints continue to be served by Quarkus HTTP. Camel routes that use `platform-http` can even share the same HTTP server.

- **Same configuration.** Camel settings go in the same `application.properties`, under the `camel.*` namespace. Quarkus profiles, config sources, and environment variable overrides all apply.

- **Dev mode works.** `quarkus dev` hot-reloads your Camel routes along with everything else. Change a route, save the file, it takes effect immediately.

- **Native compilation.** Camel Quarkus extensions are built for GraalVM native image. If your app compiles native today, adding Camel routes won't break that.

## Going further

This post covered three patterns, but Camel's 400+ components cover databases, cloud services, messaging systems, SaaS APIs, and protocols from FTP to AS2. Each follows the same model: add the `camel-quarkus-*` extension, use its URI in a route.

Camel implements the [Enterprise Integration Patterns](https://camel.apache.org/components/next/eips/enterprise-integration-patterns.html). Here is a few that would naturally extend this example:

- **Content-Based Router:** use `.choice().when()` to route priority orders to a fast-track queue and standard orders to the normal flow.

- **Wire Tap:** use `.wireTap("seda:audit")` to send a copy of the order event to an audit log without slowing down the main route.

- **Throttle:** use `.throttle(10)` to rate-limit calls to the partner loyalty API.

- **Dead Letter Channel:** replace the `onException` log-and-swallow with `errorHandler(deadLetterChannel("file:orders/errors"))` to persist failed order events to disk for later inspection or reprocessing.

- **More protocols:** need to call a SOAP service, send to an AMQP broker, or drop files on an SFTP server? Each has a Camel Quarkus extension.

To explore more:

- [Camel Quarkus documentation](https://camel.apache.org/camel-quarkus/latest/)
- [Camel Quarkus examples on GitHub](https://github.com/apache/camel-quarkus-examples)
- [Try Camel on Quarkus in the Developer Sandbox](https://developers.redhat.com/articles/2023/10/06/try-camel-quarkus-developer-sandbox-red-hat-openshift)
- [Red Hat build of Apache Camel for Quarkus](TODO) — downstream documentation and support

The companion sample project for this post is available on [GitHub](TODO). Clone it and `mvn quarkus:dev` to see all three routes in action.
