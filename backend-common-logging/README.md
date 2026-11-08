# Backend Common Logging — Java 8

Satu plain JAR untuk **Java 8 dan Spring Boot 2.4.3/2.4.4**, dengan dua facade: `TLog` untuk lifecycle proses dan `TExceptionLog` untuk error boundary. Namespace `io.github.commonlogging`.

[Build, instalasi dan kompatibilitas](JAVA8-COMPATIBILITY.md) · [Trace/span](TRACE-CONTEXT.md) · [Payload dan event](ENTERPRISE-LOGGING.md) · [Contoh CRUD/flow kompleks](../examples/insurance-crud/README.md) · [Rancangan](../docs/insurance-common/README.md)

## Build satu JAR

Dengan JDK 8 + Maven 3.x, dari root repository:

```bash
bash scripts/build-java8.sh
```

Hasil: `backend-common-logging/target/backend-common-logging-0.1.0-SNAPSHOT.jar`. Library bukan executable aplikasi dan tidak membundel dependencies/framework atau examples. Tidak melakukan publish/deploy.

```bash
# Memverifikasi satu JAR yang sama di dua versi Boot:
bash scripts/verify-java8.sh

# Memasang artifact + POM di Maven lokal:
mvn -f backend-common-logging/pom.xml install -DskipTests
```

Dependency consumer:

```xml
<dependency>
  <groupId>io.github.commonlogging</groupId>
  <artifactId>backend-common-logging</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Consumer menyediakan Spring MVC/web dan AOP; actuator opsional untuk metrics. Auto-configuration menggunakan `spring.factories`. Jika memakai systemPath/JAR langsung seperti example, declare dependency runtime sendiri; [detail](JAVA8-COMPATIBILITY.md).

## Pemakaian

```java
@LogPayload
public final class ClaimRequest {
    @LogMask private String participantName;
    @LogMask(strategy = MaskStrategy.LAST_FOUR) private String policyNumber;
    @LogValue private String productCode;
    @LogExclude private String accessToken;
    // Constructor/getters sesuai DTO aplikasi.
}

@LoggedOperation("claim.check")
@LogValue
public boolean check(
    @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogValue("eligible") boolean eligible,
    @LogExclude String accessToken,
    ClaimRequest request) {
    tLog.info("claim.check.rules", "Eligibility rules completed");
    return eligible;
}
```

Field DTO tanpa aturan dilewati. Exclude/mask yang lebih ketat pada interface/superclass tetap menang. Input/result diproses serializer log khusus, tidak mengubah DTO atau response HTTP bisnis. Raw body/header/credential/binary/exception message tidak di-dump. `TLog.info` menerima safe message yang menjadi tanggung jawab pemanggil.

Global payload default false. `@LoggedOperation(logInput=false, logResult=false)` menonaktifkan payload per method. Aspect synchronous menangani start/end/fail, class/method, nested span, durasi dan payload tersanitasi. Self-invocation/private/manual instance tidak melewati proxy; Future/reactive/@Async dilewati.

```yaml
backend:
  common:
    logging:
      application: claims-api
      environment: development
      application-package: com.company.insurance
      payload-enabled: false
      payload-paths: [/api/claims, /api/claims/*]
      propagation-hosts: [core-insurance.internal]
```

Filter membuat/menggunakan satu SERVER span. OpenTelemetry SDK menangani traceId/spanId/parentSpanId dan W3C; RestTemplateBuilder Spring Boot mendapat interceptor untuk host allowlist. `DependencyLogging.callHttp` membuka scope INTERNAL di atas transport CLIENT. SDK tanpa instrumentasi memakai `call` empat argumen dan header traceparent eksplisit.

## Error boundary dan job

Library tidak memasang global response handler. Inject `ExceptionLoggingBridge`, panggil `bridge.log(ex)` dari handler existing, kemudian jalankan response mapping bisnis. Layer rethrow hanya mencatat lifecycle failure; satu pemilik boundary mencatat cause types/frames. Override ErrorClassifier untuk code/category/expected rejection aplikasi. Exception message sengaja tidak dicetak.

```java
try (TraceScope job = tLog.withTrace(null)) {
    try (LogOperation operation = tLog.start("batch.process", requestDto)) {
        try {
            Result result = process();
            operation.end(result);
        } catch (RuntimeException ex) {
            operation.fail(ex);
            tExceptionLog.error(operation.context(), ex);
            throw ex;
        }
    }
}
```

`close` tidak menandai success; tanpa terminal menjadi PROCESS_INCOMPLETE. `withTrace(null)` memiliki real CONSUMER span untuk kelompok pekerjaan. Executor menggunakan `tLog.propagate(task)` secara eksplisit; scope/MDC dipulihkan setelah task. Ini bukan dukungan completion semua async API.

## Output dan verifikasi

Logger `common.logging` menulis sanitized JSON schemaVersion 3 melalui SLF4J 1.7. Pakai dedicated appender `%msg%n` seperti example agar menjadi JSON top-level, atau override EventSink sesuai backend kantor. Micrometer menghitung kegagalan sink/payload dan status ERROR Logback. Tidak ada auto export ke collector atau retry bisnis.

Test berjalan pada JDK 8 nyata dan memeriksa runtime Boot aktual, bytecode 52, auto-configuration, payload/privacy, generic-interface annotations, SDK exported spans, context cleanup, servlet request nyata, transaksi, CRUD H2 dan flow kompleks. [Script matriks](../scripts/verify-java8.sh) menjaga checksum binary yang sama pada kedua consumer Boot.

Formatter dijalankan dengan JDK 17+ (`mvn spotless:apply`/`spotless:check`); build/test library tetap JDK 8. Integrasi kantor, collector, security chain, beban, akses dan retention belum diverifikasi. Migrasi Java 21/Boot 3 akan membutuhkan verifikasi integrasi tersendiri.
