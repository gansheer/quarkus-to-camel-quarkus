# Quarkus to Camel Quarkus

Sample projects showing how to extend existing Quarkus applications with [Apache Camel](https://camel.apache.org/) using [Camel Quarkus](https://camel.apache.org/camel-quarkus/) extensions.

Each subdirectory is a self-contained example with its own README, typically pairing a "before" (plain Quarkus) and "after" (Camel-extended) version of the same application.

## Examples

| Example | Description |
|---|---|
| [extend-with-camel-quarkus](extend-with-camel-quarkus/) | Add Camel routes to a Quarkus REST order service -- Kafka events, file ingestion, HTTP calls with retry |

## Prerequisites

- JDK 21+
- Maven 3.9+
- Podman (or Docker) -- for Quarkus Dev Services
