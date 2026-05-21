# feature-flag

Shared feature flag library for Spring Boot services with:

- **HTTP Bootstrap** — fetches all service flags on application startup
- **Multi-broker Messaging** — keeps the cache up to date in real time via Kafka, RabbitMQ, or ActiveMQ Artemis
- **Caffeine Local Cache** — zero-latency lookups, no time-based expiration
- **Layered Fallback** — never brings down the application if the flag microservice is unavailable
- **Field Injection** — inject the live flag value into a `Boolean` field for in-method conditional logic
- **Optional OAuth2** — automatically attaches a Bearer token to the HTTP bootstrap when `OAuth2AuthorizedClientManager` is present

---

## How it works

```
Application starts
      │
      ▼
HTTP Bootstrap → GET /flags/{service-name}/{environment}
      │
      ├── Success → populates Caffeine cache with all flags for this service and environment
      └── Failure → application starts with configured default values (startup is not blocked)
      │
      ▼
Messaging consumer listens: feature-flags.events (Kafka / RabbitMQ / Artemis)
      │
      ├── CREATED  → filters by serviceName, adds flag as disabled (false)
      ├── UPDATED  → filters by serviceName + environmentName, updates cache value
      └── DELETED  → removes from cache regardless of environment
      │
      ▼
@FeatureFlag intercepts the call
      │
      ├── Cache hit  → uses the cached value
      └── Cache miss → cascading fallback:
                       1. feature-flag.defaults.* (application.yaml)
                       2. @FeatureFlag(enabledByDefault)
      │
      ▼
@FeatureFlag on a Boolean field (optional)
      │
      └── Before each method call → field is updated with the current cache value
```

---

## Installation

```xml
<dependency>
    <groupId>io.github.cassiocintra</groupId>
    <artifactId>feature-flag</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Configuration

### Minimal (application.yaml)

```yaml
feature-flag:
  service-name: checkout-service
  environment: ${spring.profiles.active:dev}
  flag-service-url: http://flag-management-service/feature-flag/v1

# Defaults — used ONLY if the HTTP bootstrap fails
# Take precedence over @FeatureFlag(enabledByDefault)
  defaults:
    new-checkout: false
    pix-payment: false
```

### Messaging broker (choose one)

**Kafka:**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

feature-flag:
  kafka:
    topic: feature-flags.events          # default: feature-flags.events
    group-id: feature-flag-consumer      # default: feature-flag-consumer
```

**RabbitMQ:**
```yaml
spring:
  rabbitmq:
    host: localhost

feature-flag:
  rabbit:
    queue: feature-flags.events          # default: feature-flags.events
```

**ActiveMQ Artemis:**
```yaml
spring:
  artemis:
    broker-url: tcp://localhost:61616

feature-flag:
  artemis:
    queue: feature-flags.events          # default: feature-flags.events
```

> Each broker requires its own starter — see [Messaging support](#messaging-support) below.

---

## Usage

```java
// Simple flag — throws FeatureDisabledException if disabled
@FeatureFlag("new-checkout")
public ResponseEntity<Order> checkout(OrderRequest request) {
    // executes only if "new-checkout" is true in the current environment
}

// With default value if bootstrap fails and no properties default is defined
@FeatureFlag(value = "report-v2", enabledByDefault = true)
public byte[] generateReport() { ... }

// With custom exception message
@FeatureFlag(value = "pix-payment", message = "Pix payment is not yet available.")
public void payWithPix() { ... }

// Applied to the entire class — all methods are protected by the same flag
@FeatureFlag("beta-module")
@RestController
public class BetaController { ... }

// Field injection — Boolean field updated before every method call
@Service
public class PaymentService {

    @FeatureFlag("new-payment-flow")
    private Boolean newPaymentFlowEnabled;

    public void process(Order order) {
        if (Boolean.TRUE.equals(newPaymentFlowEnabled)) {
            // new flow
        } else {
            // legacy flow
        }
    }
}
```

---

## Handling the exception (optional)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<String> handleFeatureDisabled(FeatureDisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .body(ex.getMessage());
    }
}
```

---

## Messaging support

Each broker is optional — only add the starter for the broker your service uses.
The library detects which one is configured and activates the correct consumer automatically.

| Broker | Starter | Activation property |
|---|---|---|
| Kafka | `spring-boot-starter-kafka` (via `spring-kafka`) | `spring.kafka.bootstrap-servers` |
| RabbitMQ | `spring-boot-starter-amqp` | `spring.rabbitmq.host` |
| ActiveMQ Artemis | `spring-boot-starter-artemis` | `spring.artemis.broker-url` |

> **Note:** ActiveMQ Classic (`spring-boot-starter-activemq`) is not supported due to known security vulnerabilities (CVE-2026-33227, CVE-2026-34197). Use ActiveMQ Artemis instead.

### Kafka dependency

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### RabbitMQ dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### ActiveMQ Artemis dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-artemis</artifactId>
</dependency>
```

---

## Messaging events

The flag microservice publishes broadcast events to the configured topic.
Each consumer service filters relevant events by `serviceName` and `environmentName`.

**CREATED** — flag always starts disabled for all environments:
```json
{
  "flagName":    "new-checkout",
  "serviceName": "checkout-service",
  "action":      "CREATED"
}
```

**UPDATED** — flag value changed in a specific environment:
```json
{
  "flagName":        "new-checkout",
  "serviceName":     "checkout-service",
  "environmentName": "prod",
  "enabled":         true,
  "action":          "UPDATED"
}
```

**DELETED** — flag removed from all environments:
```json
{
  "flagName":    "new-checkout",
  "serviceName": "checkout-service",
  "action":      "DELETED"
}
```

---

## OAuth2 support (optional)

If the flag microservice requires JWT authentication, the library automatically attaches
a Bearer token using Spring's `OAuth2AuthorizedClientManager` — no extra configuration
in the library is needed.

### How to enable

**1. Add the dependency:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

**2. Declare an `OAuth2AuthorizedClientManager` bean:**
```java
@Configuration
public class OAuth2Config {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider provider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }
}
```

**3. Configure your OAuth2 client:**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          feature-flags:
            client-id: my-service
            client-secret: ${CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: openid
        provider:
          feature-flags:
            issuer-uri: http://keycloak-host/realms/my-realm
```

When the `OAuth2AuthorizedClientManager` bean is present, the library uses
`OAuth2ClientHttpRequestInterceptor` to fetch, cache, and renew the token automatically.

---

## Fallback hierarchy

When a flag is not in the cache (bootstrap failed or Kafka event not yet received):

```
1. feature-flag.defaults.*        ← application.yaml of the consumer service
2. @FeatureFlag(enabledByDefault) ← value defined on the annotation (default: false)
```

The cache never expires by time — it relies on messaging events to stay up to date.
Flags removed via `DELETED` are evicted from the cache immediately.

---

## Bean behavior (Autoconfigure)

All beans are registered conditionally — no errors if a broker or HTTP endpoint is absent:

| Bean | Condition |
|---|---|
| `FeatureFlagCacheService` | Always active |
| `FeatureFlagEventProcessor` | Always active |
| `FeatureFlagAspect` | Always active |
| `FeatureFlagFieldInjectorAspect` | Always active |
| `FeatureFlagBootstrap` | Requires `feature-flag.flag-service-url` |
| `featureFlagRestClient` (no auth) | Requires `feature-flag.flag-service-url` + no `OAuth2AuthorizedClientManager` in context |
| `featureFlagRestClient` (OAuth2) | Requires `feature-flag.flag-service-url` + `spring-boot-starter-oauth2-client` + `OAuth2AuthorizedClientManager` bean |
| `FeatureFlagKafkaConsumer` | Requires `spring-kafka` on classpath + `spring.kafka.bootstrap-servers` |
| `FeatureFlagRabbitConsumer` | Requires `spring-amqp` on classpath + `spring.rabbitmq.host` |
| `FeatureFlagActiveMqConsumer` | Requires `spring-boot-starter-artemis` on classpath + `spring.artemis.broker-url` |

---

## Project structure

```
feature-flag/
├── pom.xml
└── src/
    └── main/java/cassio/annotations/
        ├── FeatureFlag.java                         ← the annotation
        ├── aspect/
        │   ├── FeatureFlagAspect.java               ← intercepts methods via AOP, blocks if disabled
        │   └── FeatureFlagFieldInjectorAspect.java  ← updates @FeatureFlag Boolean fields before each method
        ├── bootstrap/
        │   └── FeatureFlagBootstrap.java            ← HTTP fetch on startup
        ├── cache/
        │   └── FeatureFlagCacheService.java         ← Caffeine cache, source of truth
        ├── config/
        │   ├── FeatureFlagAutoConfig.java           ← registers core beans
        │   ├── FeatureFlagOAuth2Config.java         ← OAuth2 RestClient (optional)
        │   └── FeatureFlagProperties.java           ← reads application.yaml
        ├── exception/
        │   └── FeatureDisabledException.java        ← thrown when flag is disabled
        ├── messaging/
        │   ├── FeatureFlagEventProcessor.java       ← shared event processing logic
        │   ├── activemq/
        │   │   ├── FeatureFlagActiveMqConfig.java   ← Artemis config (optional)
        │   │   └── FeatureFlagActiveMqConsumer.java ← Artemis JMS listener
        │   ├── kafka/
        │   │   ├── FeatureFlagKafkaConfig.java      ← Kafka config (optional)
        │   │   └── FeatureFlagKafkaConsumer.java    ← Kafka listener
        │   └── rabbit/
        │       ├── FeatureFlagRabbitConfig.java     ← RabbitMQ config (optional)
        │       └── FeatureFlagRabbitConsumer.java   ← RabbitMQ listener
        ├── model/
        │   └── FeatureFlagEvent.java                ← messaging event model
        └── utils/
            └── JsonUtils.java                       ← Jackson 3 serialization helper
```
