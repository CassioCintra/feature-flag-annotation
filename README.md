# feature-flag-core

Shared feature flag library with:
- **HTTP Bootstrap** — fetches all service flags on application startup
- **Kafka Consumer** — keeps the cache up to date in real time via events
- **Caffeine Local Cache** — zero-latency lookups, no time-based expiration
- **Layered Fallback** — never brings down the application if the flag microservice is unavailable
- **Field Injection** — inject the live flag value into a `Boolean` field for in-method conditional logic

---

## How it works

```
Application starts
      │
      ▼
HTTP Bootstrap → GET /flags/{service-name}/{environment}
      │
      ├── Success → populates Caffeine cache with all flags for this service and environment
      └── Failure → application starts with configured default values
      │             (startup is not blocked)
      ▼
Kafka Consumer listens: feature-flags.events (broadcast)
      │
      ├── CREATED         → filters by serviceName, adds flag as disabled (false)
      ├── UPDATED         → filters by serviceName + environmentName, updates cache value
      └── DELETED         → removes from cache regardless of environment
      │
      ▼
@FeatureFlag intercepts the call
      │
      ├── Cache hit  → uses the cached value
      └── Cache miss → cascading fallback:
                       1. feature-flag.defaults.* (application.properties)
                       2. @FeatureFlag(enabledByDefault)

      │
      ▼
@FeatureFlag on a Boolean field (optional)
      │
      └── Before each method call → field is updated with the current cache value
                                    (always in sync with Kafka events)
```

---

## Installation

```xml
<dependency>
    <groupId>cassio</groupId>
    <artifactId>feature-flag-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Configuration (consumer service application.properties)

```properties
# Service identity
feature-flag.service-name=checkout-service
feature-flag.environment=${spring.profiles.active:dev}
feature-flag.flag-service-url=http://flag-management-service

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# (Optional) Customize the topic and consumer group-id
feature-flag.kafka.topic=feature-flags.events
feature-flag.kafka.group-id=checkout-service-flag-consumer

# Defaults — used ONLY if the HTTP bootstrap fails
# Take precedence over @FeatureFlag(enabledByDefault)
feature-flag.defaults.new-checkout=false
feature-flag.defaults.pix-payment=false
```

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
// Useful for branching between a new and a legacy flow within the same method
@Service
public class PaymentService {

    @FeatureFlag("new-payment-flow")
    private Boolean newPaymentFlowEnabled;

    public void process(Order order) {
        if (Boolean.TRUE.equals(newPaymentFlowEnabled)) {
            // new flow — executes when flag is enabled
        } else {
            // legacy flow — executes when flag is disabled
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

## Kafka Events

The flag microservice publishes broadcast events to the `feature-flags.events` topic.
Each consumer service filters relevant events by `serviceName` and `environmentName`.

**CREATED** — published when a flag is created. Always starts as disabled for all environments.
`environmentName` and `enabled` are not present:
```json
{
  "flagName":    "new-checkout",
  "serviceName": "checkout-service",
  "action":      "CREATED"
}
```

**UPDATED** — published when a flag value changes in a specific environment:
```json
{
  "flagName":         "new-checkout",
  "serviceName":      "checkout-service",
  "environmentName":  "prod",
  "enabled":          true,
  "action":           "UPDATED"
}
```

**DELETED** — published when a flag is removed. `environmentName` and `enabled` are not present
since deletion affects all environments:
```json
{
  "flagName":    "new-checkout",
  "serviceName": "checkout-service",
  "action":      "DELETED"
}
```

---

## Fallback hierarchy

When a flag is not in the cache (bootstrap failed or flag not yet received via Kafka):

```
1. feature-flag.defaults.*        ← application.properties of the consumer service
2. @FeatureFlag(enabledByDefault) ← value defined directly on the annotation
```

The cache never expires by time — it relies on Kafka to keep the state up to date.
Flags removed via `DELETED` are evicted from the cache immediately.

---

## Bean behavior (Autoconfigure)

Beans are registered automatically but only activated when conditions are met:

| Bean | Condition |
|---|---|
| `FeatureFlagCacheService` | Always active |
| `FeatureFlagAspect` | Always active |
| `FeatureFlagFieldInjectorAspect` | Always active |
| `FeatureFlagBootstrap` | Requires `feature-flag.flag-service-url` |
| `FeatureFlagKafkaConsumer` | Requires `spring.kafka.bootstrap-servers` |

This allows the library to be used in tests or environments without Kafka or HTTP without startup errors.

---

## Project structure

```
feature-flag-core/
├── pom.xml
└── src/
    ├── main/java/cassio/featureflag/
    │   ├── annotation/
    │   │   └── FeatureFlag.java                ← the annotation
    │   ├── aspect/
    │   │   ├── FeatureFlagAspect.java              ← intercepts methods via AOP, blocks if disabled
    │   │   └── FeatureFlagFieldInjectorAspect.java ← updates @FeatureFlag Boolean fields before each method
    │   ├── bootstrap/
    │   │   └── FeatureFlagBootstrap.java       ← HTTP fetch on startup
    │   ├── cache/
    │   │   └── FeatureFlagCacheService.java    ← Caffeine cache, source of truth
    │   ├── config/
    │   │   ├── FeatureFlagAutoConfig.java      ← registers all beans
    │   │   └── FeatureFlagProperties.java      ← reads application.properties
    │   ├── exception/
    │   │   └── FeatureDisabledException.java   ← thrown when flag is disabled
    │   ├── kafka/
    │   │   └── FeatureFlagKafkaConsumer.java   ← updates cache via Kafka events
    │   └── model/
    │       └── FeatureFlagEvent.java           ← Kafka event model
    └── test/java/cassio/featureflag/
        ├── aspect/
        │   ├── FeatureFlagAspectTest.java                  ← tests AOP interception
        │   ├── FeatureFlagAspectTestConfig.java
        │   ├── FeatureFlagFieldInjectorAspectTest.java     ← tests Boolean field injection
        │   ├── FeatureFlagFieldInjectorAspectTestConfig.java
        │   ├── FakeService.java
        │   └── FakeServiceWithFlagField.java
        ├── cache/
        │   └── FeatureFlagCacheServiceTest.java
        ├── exception/
        │   └── FeatureDisabledExceptionTest.java
        └── kafka/
            └── FeatureFlagKafkaConsumerTest.java
```