# feature-flag

Shared feature flag library for Spring Boot services with:

- **HTTP Bootstrap** — fetches all service flags on application startup
- **Strict Mode** — opt-in: blocks startup if any annotated flag is not found on the server
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
FeatureFlagScanner → scans all Spring beans for @FeatureFlag annotations (used by strict mode)
      │
      ▼
GET /flags?service={name}&env={env}
      │
      ├── Success → populates Caffeine cache with all flags for this service and environment
      └── Failure → application starts with configured default values (startup is not blocked)
      │
      ▼
[strict=true] enforces that every annotated flag exists in the server response
      │                → throws IllegalStateException and blocks startup if any flag is missing
      ▼
Messaging consumer listens: flag.events (Kafka / RabbitMQ / Artemis)
      │
      ├── CREATED / UPDATED / TOGGLED → filters by serviceName + environments map,
      │                                  caches enabled AND environments[currentEnv]
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
  flag-service-url: http://ms-feature-flags:8081/feature-flag/v1

# Defaults — used ONLY if the HTTP bootstrap fails
# Take precedence over @FeatureFlag(enabledByDefault)
  defaults:
    new-checkout: false
    pix-payment: false
```

### All options

```yaml
feature-flag:
  service-name: checkout-service                              # required
  environment: ${spring.profiles.active:dev}                 # required
  flag-service-url: http://ms-feature-flags:8081/feature-flag/v1  # enables bootstrap
  strict: false                                              # true: blocks startup if any annotated flag is missing
  defaults:
    new-checkout: false
```

### Messaging broker (choose one)

**Kafka:**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

feature-flag:
  kafka:
    topic: flag.events                 # default: flag.events
    group-id: order-service-consumer   # default: feature-flag-consumer
```

**RabbitMQ:**
```yaml
spring:
  rabbitmq:
    host: localhost

feature-flag:
  rabbit:
    queue: flag.events          # default: flag.events
```

**ActiveMQ Artemis:**
```yaml
spring:
  artemis:
    broker-url: tcp://localhost:61616

feature-flag:
  artemis:
    queue: flag.events          # default: flag.events
```

> Each broker requires its own starter — see [Messaging support](#messaging-support) below.

---

## Usage

### Short form (backward-compatible)

```java
// Throws FeatureDisabledException if disabled
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
```

### Full form — with metadata for auto-registration

```java
@FeatureFlag(
    key       = "checkout_v2",
    type      = FlagType.ROLLOUT,
    rollout   = 30,                          // % of traffic
    envs      = { "production", "staging" },
    tags      = { "payments", "checkout" },
    service   = "checkout-api",              // overrides global service-name for this flag
    owner     = "payments-team",
    expiresAt = "2026-09-01"                 // ISO date — reminder to remove the flag
)
public void processCheckout() { ... }
```

The `key` attribute takes precedence over `value` when both are set. The metadata fields (`type`, `rollout`, `envs`, `tags`, `owner`, `expiresAt`) are used by strict mode to identify annotated flags and can serve as in-code documentation of the flag's intent.

### Class-level

```java
// All methods in this controller are protected by the same flag
@FeatureFlag("beta-module")
@RestController
public class BetaController { ... }
```

### Field injection

```java
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

## Strict mode

When `feature-flag.strict=true`, the library compares every flag key found by scanning `@FeatureFlag` annotations against the flags returned by the server for the current service and environment. If any annotated flag is missing, startup is blocked:

```
IllegalStateException: Strict mode is active. The following feature flags are annotated
in the code but not found in the flag server for service 'checkout-api' / environment
'production': [payments_v3]. Register them before starting this application.
```

Without strict mode (default), a missing flag falls back to `feature-flag.defaults.*` or `@FeatureFlag(enabledByDefault)`, and a warning is logged.

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

All mutations publish to the single topic `flag.events`. The `action` field distinguishes the event type.
Each consumer filters by `serviceName` and checks if its environment is present in `environments`.

The effective cached state is: `enabled AND environments[currentEnv]`.

**CREATED / UPDATED / TOGGLED:**
```json
{
  "flagName":    "new-checkout",
  "serviceName": "checkout-service",
  "enabled":     true,
  "environments": {
    "dev":     true,
    "staging": true,
    "prod":    false
  },
  "action": "CREATED"
}
```

| `action`  | When |
|-----------|------|
| `CREATED` | Flag created |
| `UPDATED` | Metadata updated (type, rollout, tags…) |
| `TOGGLED` | `enabled` or per-environment state changed |
| `DELETED` | Flag removed — evicted from cache regardless of environment |

**DELETED:**
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

When a flag is not in the cache (bootstrap failed or messaging event not yet received):

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
| `FeatureFlagScanner` | Always active |
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
        │   ├── FeatureFlagBootstrap.java            ← scan → load flags on startup
        │   ├── FeatureFlagScanner.java              ← scans Spring beans for @FeatureFlag annotations
        │   └── FeatureFlagServerResponse.java       ← DTO for GET /flags response
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
        │   │   ├── FeatureFlagKafkaConsumer.java    ← Kafka listener
        │   │   └── FeatureFlagKafkaProperties.java  ← topic + group-id (configurable)
        │   └── rabbit/
        │       ├── FeatureFlagRabbitConfig.java     ← RabbitMQ config (optional)
        │       └── FeatureFlagRabbitConsumer.java   ← RabbitMQ listener
        ├── model/
        │   ├── FeatureFlagEvent.java                ← messaging event model
        │   └── FlagType.java                        ← enum: BOOLEAN, ROLLOUT, EXPERIMENT
        └── utils/
            └── JsonUtils.java                       ← Jackson serialization helper
```
