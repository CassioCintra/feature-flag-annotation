# feature-flag

Shared feature flag library for Spring Boot services with:

- **Consul KV** — loads the service flags on startup and follows every change with Consul blocking queries
- **Per-workspace and per-environment isolation** — each instance reads only `feature-flags/{workspace}/{service}/{environment}/`
- **Strict Mode** — opt-in: blocks startup if any annotated flag is not found in Consul
- **Caffeine Local Cache** — zero-latency lookups, no time-based expiration
- **Layered Fallback** — never brings down the application if Consul is unavailable
- **Field Injection** — inject the live flag value into a `Boolean` field for in-method conditional logic
- **Consul ACL login** — exchanges an OAuth2 Client Credentials JWT for a Consul token and renews it automatically

---

## How it works

```
Application starts
      │
      ▼
GET /v1/kv/feature-flags/{workspace}/{service}/{environment}/?recurse
      │
      ├── Success → replaces the Caffeine cache with the flags found
      └── Failure → application starts with configured default values (startup is not blocked)
      │
      ▼
[strict=true] enforces that every annotated flag exists in Consul
      │                → throws IllegalStateException and blocks startup if any flag is missing
      ▼
Virtual thread watches the same prefix (Consul blocking query)
      │
      ├── Flag changed or added → cache updated
      ├── Flag removed          → evicted from the cache, falls back to the defaults
      └── Consul unavailable    → retries every 5s, the cache keeps the last known values
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

## Consul layout

The flags are written by `ms-feature-flags-management`, the source of truth. Each key holds
the effective value of one flag in one environment of one workspace:

```
feature-flags/
└── 7a1c0e2e-4d5b-4c8e-9f10-2b3c4d5e6f70/      ← workspace (UUID)
    └── checkout-service/
        ├── dev/
        │   └── new-checkout   → {"enabled": true}
        └── prod/
            └── new-checkout   → {"enabled": false}
```

The workspace comes from the `workspace_id` claim of the JWT used to log in to Consul, so the
application does not configure it. Without a token, set `feature-flag.workspace-id`.

Keys with any other value are ignored and logged as a warning.

---

## Installation

```xml
<dependency>
    <groupId>io.github.cassiocintra</groupId>
    <artifactId>feature-flag</artifactId>
    <version>3.0.0</version>
</dependency>
```

---

## Configuration

```yaml
feature-flag:
  service-name: checkout-service                 # required
  environment: ${spring.profiles.active:dev}     # required
  strict: false                                  # true: blocks startup if any annotated flag is missing
  workspace-id:                                  # only without a token; otherwise read from the JWT
  consul:
    url: http://consul:8500                      # enables the Consul sync
    auth-method: ms-auth                         # default: ms-auth
    client-registration-id: feature-flag         # default: feature-flag
  defaults:                                      # used while a flag is not in Consul
    new-checkout: false
    pix-payment: false
```

> [!WARNING]
> **Every default must be `true` or `false`.** A default left empty (`new-checkout:`) or with any
> other value (`new-checkout: yes`) makes the flag **disabled**, even if the annotation says
> `enabledByDefault = true`. The application still starts, and a warning is logged for each
> such flag:
>
> ```
> Default for flag 'new-checkout' is '', not true or false. The flag will be treated as disabled.
> ```

---

## Usage

### Short form (backward-compatible)

```java
// Throws FeatureDisabledException if disabled
@FeatureFlag("new-checkout")
public ResponseEntity<Order> checkout(OrderRequest request) {
    // executes only if "new-checkout" is true in the current environment
}

// Default used while the flag is not in Consul and no feature-flag.defaults entry exists
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

When `feature-flag.strict=true`, the library compares every flag key found by scanning `@FeatureFlag` annotations against the flags found in Consul for the current service and environment. If any annotated flag is missing, startup is blocked:

```
IllegalStateException: Strict mode is active. The following feature flags are annotated
in the code but not found in Consul for service 'checkout-api' / environment
'production': [payments_v3]. Register them before starting this application.
```

Without strict mode (default), a missing flag falls back to `feature-flag.defaults.*` or `@FeatureFlag(enabledByDefault)`, and a warning is logged.
If Consul is unavailable on startup, strict mode is not checked.

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

## Consul ACL login

With ACL enabled, the library logs in through a Consul JWT auth method (`/v1/acl/login`)
using an access token from your OAuth2 provider. The token needs the `flags:read` scope,
the `consul` audience and a `workspace_id` claim with the UUID of the workspace; Consul only
lets it read `feature-flags/{workspace_id}/`. When Consul rejects the token (it lasts up to 15 minutes),
the library logs in again.

Without an `OAuth2AuthorizedClientManager` bean, Consul is called without a token.

**1. Add the dependency:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-client</artifactId>
</dependency>
```

**2. Declare an `OAuth2AuthorizedClientManager` bean.** The flags are read on a background
thread, outside any HTTP request, so use `AuthorizedClientServiceOAuth2AuthorizedClientManager`:
```java
@Configuration
public class OAuth2Config {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        var provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }
}
```

**3. Configure the client registration** (its id must match `feature-flag.consul.client-registration-id`):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          feature-flag:
            client-id: checkout-service
            client-secret: ${CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: flags:read
            provider: ms-auth
        provider:
          ms-auth:
            issuer-uri: http://ms-auth:9000
```

---

## Fallback hierarchy

When a flag is not in the cache (Consul unavailable on startup, or no key for the flag):

```
1. feature-flag.defaults.*        ← application.yaml of the consumer service
2. @FeatureFlag(enabledByDefault) ← value defined on the annotation (default: false)
```

The cache never expires by time. It is replaced on every change under the service prefix in Consul.

---

## Bean behavior (Autoconfigure)

| Bean | Condition |
|---|---|
| `FeatureFlagCacheService` | Always active |
| `FeatureFlagAspect` | Always active |
| `FeatureFlagFieldInjectorAspect` | Always active |
| `FeatureFlagScanner` | Always active |
| `ConsulFlagSync` | Requires `feature-flag.consul.url` |
| `ConsulJwtSupplier` | Requires `feature-flag.consul.url` + `spring-boot-starter-security-oauth2-client` + `OAuth2AuthorizedClientManager` bean |

---

## Migrating from 2.x

- Consul keys moved from `feature-flags/{service}/{environment}/` to `feature-flags/{workspace}/{service}/{environment}/`.
  `ms-feature-flags-management` writes the new layout.
- The OAuth2 client of the application must be registered with a workspace, so its token carries the `workspace_id` claim.
  Without a token, set `feature-flag.workspace-id`.
- `ConsulFlagSync` takes the workspace resolver as a new constructor argument.

## Migrating from 1.x

- Kafka, RabbitMQ and ActiveMQ Artemis consumers were removed. Remove `feature-flag.kafka.*`, `feature-flag.rabbit.*` and `feature-flag.artemis.*`.
- The HTTP bootstrap was removed. Replace `feature-flag.flag-service-url` with `feature-flag.consul.url`.
- An empty or invalid entry in `feature-flag.defaults` now disables the flag (see the warning above).
- 1.x shipped a broken auto-configuration registration; 2.0.0 fixes it.

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
        │   └── FeatureFlagScanner.java              ← scans Spring beans for @FeatureFlag annotations
        ├── cache/
        │   └── FeatureFlagCacheService.java         ← Caffeine cache
        ├── config/
        │   ├── FeatureFlagAutoConfig.java           ← registers core beans
        │   ├── FeatureFlagOAuth2Config.java         ← JWT for the Consul login (optional)
        │   └── FeatureFlagProperties.java           ← reads application.yaml
        ├── consul/
        │   ├── ConsulClient.java                    ← Consul KV reads and ACL login
        │   ├── ConsulFlagSync.java                  ← initial load, strict mode and watch
        │   └── ConsulJwtSupplier.java               ← source of the JWT used to log in
        ├── exception/
        │   └── FeatureDisabledException.java        ← thrown when flag is disabled
        └── model/
            └── FlagType.java                        ← enum: BOOLEAN, ROLLOUT, EXPERIMENT
```
