# Backend Common Logging

Library JAR dengan dua facade: **TLog** untuk proses normal dan **TExceptionLog** untuk kegagalan. Dibangun dengan Java release 17 dan diuji pada JDK 21 / Spring Boot 3.5.11 / Spring MVC. Namespace sementara: `io.github.commonlogging`; sesuaikan koordinat artifact dengan standar kantor sebelum publikasi.

[Kontrak aktif schema 3](ENTERPRISE-LOGGING.md) · [Contoh CRUD H2 Java 17](../examples/insurance-crud/README.md) · [Desain](../docs/insurance-common/README.md) · [Implementation plan dan status](../docs/insurance-common/implementation.md) · [Contoh consumer HTTP](src/test/java/com/company/pilot/SpringMvcLoggingTest.java) · [Contoh consumer job](src/test/java/com/company/pilot/JobConsumerTest.java)

## Build

```sh
mvn test
mvn package
mvn spotless:check
```

Artifact: `target/backend-common-logging-0.1.0-SNAPSHOT.jar`. Ini plain JAR, bukan executable Spring Boot application. Tidak melakukan publish/deploy. Maven 3.6.3+ diperlukan. Di workspace ini Maven tersedia melalui `/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`.

## Pasang pada consumer

Consumer menyediakan Spring Boot starter web, starter aop, starter actuator, dan micrometer-tracing-bridge-otel sesuai dependency management-nya. Untuk export tambahkan exporter OTLP. Lihat [konfigurasi lengkap](ENTERPRISE-LOGGING.md). Dependency Spring pada library bersifat optional supaya aplikasi menentukan stack sendiri; baseline yang terverifikasi masih Boot 3.5.11, bukan klaim kompatibilitas semua versi.

```xml
<dependency>
  <groupId>io.github.commonlogging</groupId>
  <artifactId>backend-common-logging</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Import auto-configuration terdaftar di JAR. Tidak perlu component scan package library. Mode default menulis JSON message untuk kompatibilitas. Untuk encoder terstruktur Boot aktifkan `structured-arguments-enabled=true`; sink menggunakan SLF4J 2 key/value dan payload berupa objek JSON. [Pengaturan encoder dan metrics](ENTERPRISE-LOGGING.md).

```yaml
backend:
  common:
    logging:
      application: claims-api
      environment: development
      application-package: com.company.insurance
      enabled: true
      web-enabled: true
      operation-enabled: true
      payload-enabled: true
      payload-paths:
        - /claims
        - /claims/*
      trace-id-header: X-Trace-ID
      max-depth: 6
      max-items: 50
      max-string: 1024
      max-payload-bytes: 16384
      max-frames: 64
```

`payload-enabled` default false; HTTP capture memerlukan path aktif DAN tipe opt-in. Path digunakan hanya untuk pemilihan capture, bukan dicetak mentah. `application-package` menentukan frame aplikasi untuk `errorOrigin`; jika kosong origin terstruktur tidak ditebak. Ingress memvalidasi W3C `traceparent`; jika tidak ada/invalid dibuat trace baru. Response membawa `X-Trace-ID` dan `X-Span-ID` root request. Header X-Trace-ID yang dikirim client tidak dipakai sebagai input trace. [Detail trace/span dan migrasi](TRACE-CONTEXT.md).

## Masking DTO

```java
import io.github.commonlogging.annotation.*;
import io.github.commonlogging.payload.MaskStrategy;

@LogPayload
public record ClaimRequest(
    @LogMask String participantName,
    @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogExclude String accessToken,
    @LogValue String productCode) {}
```

Scalar/property DTO tanpa aturan tidak masuk log. `@LogValue` menyetujui field aman; review setiap field sebelum menambahkan annotation. Nested DTO perlu opt-in/policy; unknown object di-skip. FULL menjadi `***`; LAST_FOUR untuk string panjang >4 menjadi `***` + empat karakter terakhir, string pendek dimask penuh. Exclude menang terhadap mask, termasuk annotation inherited. DTO asli dan JSON bisnis tidak diubah; custom serializer bisnis tidak dipanggil untuk dump object ke log.

Raw string/root primitive, file/binary/stream, malformed body tanpa DTO, header, query, dan unknown object tidak di-dump. Map/external DTO memerlukan policy property allowlist; contoh bean:

```java
@Bean
PayloadPolicies payloadPolicies() {
    PayloadPolicies policies = new PayloadPolicies();
    policies.register(ExternalPolicyResponse.class, Map.of(
        "productCode", MaskStrategy.NONE,
        "policyNumber", MaskStrategy.LAST_FOUR,
        "token", MaskStrategy.EXCLUDE));
    return policies;
}
```

Policy memakai property name langsung, bukan expression/path wildcard. Nested tipe perlu policy sendiri. Ukuran/depth/item/node dibatasi dan output truncation tetap JSON valid. Unknown/skipped/cycle ditandai pada `payloadStatus`/`payloadReason`.

## Pemakaian otomatis

```java
@LoggedOperation("claim.submit")
public ClaimResponse submit(ClaimRequest request) {
    tLog.info("claim.validation", "Validation passed");
    return performSubmission(request);
}
```

Inject `TLog tLog`. Aspect menangani start/end/fail, class/method, nested span ID dan durasi. Saat payload-enabled aktif, PROCESS_START membawa input DTO yang opt-in dan PROCESS_END membawa result yang melewati serializer masking. Body advice tetap menangani payload HTTP pada root request span. Jangan menambah start/end manual pada method yang sama. Label operasi stabil, tidak mengandung input/nomor polis.

`@LoggedOperation` memiliki `logInput=true` dan `logResult=true` secara default. Nonaktifkan pada metode tertentu bila payload tidak perlu diulang:

```java
@LoggedOperation(value = "claim.lookup", logInput = false, logResult = false)
```

Satu input DTO ditulis langsung sebagai payload; bila beberapa argument berupa DTO/policy yang valid, payload berisi `arguments` dengan nama parameter dan nilai termasking. Scalar mentah seperti credential/idempotency key tidak disertakan. Parameter dapat diberi `@LogValue("nama")`, `@LogMask`, atau `@LogExclude`; scalar result memerlukan `@LogValue`/`@LogMask` pada method. Annotation interface/superclass yang lebih ketat tetap menang. Bila tidak ada input yang opt-in, serializer memberi status SKIPPED, tanpa fallback raw value. Method tanpa argument tidak memiliki payload input; void tidak memiliki payload result; null result tetap null. Return entity/wrapper yang belum opt-in diberi status SKIPPED. Wrapper DTO harus diberi `@LogPayload` beserta nested policy (lihat Submission pada contoh klaim). Flag global payload-enabled tetap berlaku; HTTP payload-paths mengatur body advice, sedangkan annotation ini adalah boundary service yang dipilih secara eksplisit.

Self-invocation melewati proxy. Aspect synchronous melewati Future/CompletionStage, return type Reactor, dan method ber-@Async; tidak mengklaim completion async. Default advice order -100 mencakup default transactional advice; custom advice/retry order perlu diverifikasi. Logging tidak mengubah transaksi atau menambah retry.

## Pemakaian manual dan job

```java
try (TraceScope job = tLog.withTrace(null)) {
    try (LogOperation operation = tLog.start("batch.process", requestDto)) {
        try {
            Result result = process();
            operation.end(result);
        } catch (RuntimeException ex) {
            operation.fail(ex); // metadata tanpa stack trace
            tExceptionLog.error(operation.context(), ex); // job adalah boundary penanganan
            throw ex;
        }
    }
}
```

Untuk exception yang diteruskan ke global HTTP handler, cukup `operation.fail(ex)` dan rethrow; handler mencatat stack trace. `close` hanya cleanup, bukan implicit success. Operasi tanpa terminal menjadi `PROCESS_INCOMPLETE`. Root operation membuat trace otomatis jika tidak ada context; untuk beberapa operasi satu job gunakan `withTrace(null)`, yang memiliki real CONSUMER span trace.scope.

`withTrace`/operation scope bersifat thread-confined. Untuk Runnable executor gunakan `tLog.propagate(task)` saat submit; wrapper memasang snapshot dan memulihkan context worker. MDC traceId/spanId/parentSpanId dipasang dan dipulihkan; ini bukan dukungan otomatis semua executor atau lifecycle future. Setelah recovery lokal gunakan `tLog.recovered(ex)` agar failure state yang sudah selesai tidak dipakai handler lain.

## Hubungkan handler existing

Library tidak memasang global response handler atau mengganti HTTP status/body. Inject `ExceptionLoggingBridge`, lalu panggil `bridge.log(ex)` dalam handler existing sebelum response mapping lama. Handler/security/job yang menangani failure adalah pemilik satu stack trace. Layer yang rethrow tidak mengulang stack trace.

Override bean `ErrorClassifier` untuk mengenali business/validation exceptions aplikasi. Hasil `ErrorDetails(category, code, expected)` memakai code uppercase stabil; expected rejection tidak mencetak stack trace. Default classifier membedakan timeout, connection unavailable, dan unexpected internal error. Consumer yang override harus tetap menyediakan classification transport yang diperlukan.

Exception message sengaja tidak dicetak otomatis karena dapat mengandung PII, SQL, atau token. Stack trace berbentuk daftar cause types dan frames dengan batas, tanpa message atau suppressed-exception detail. Ini memberi file/baris untuk diagnosis sambil menjaga pesan mentah. Origin aplikasi hanya tersedia jika package dikonfigurasi dan debug line tersedia. Pesan `tLog.info`/rejection adalah **safe message yang menjadi tanggung jawab pemanggil**; jangan memasukkan PII atau arbitrary exception message.

Untuk filter/security di luar MVC advice, pasang bridge pada error boundary yang sudah menangani respons. Filter library mencatat outcome request, tidak mengambil alih respons/exception. Exception yang keluar sebelum container menetapkan status final mencatat `responseStatusKnown=false`, bukan mengklaim status 200 sebagai hasil final.

## Dependency

Inject `DependencyLogging`; bungkus satu logical call di adapter existing:

```java
return dependencyLogging.call("policy.lookup", requestDto,
    () -> policyClient.lookup(requestDto));
```

Label menjelaskan target/operasi; input/output melewati policy payload yang sama. Response status/attempt transport tidak diintrospeksi dari semua client: metadata spesifik tetap berasal dari adapter target. DTO request/output wrapper capture memakai explicit call site dan global payload switch, bukan HTTP ingress path list. Jangan melapisi beberapa wrapper pada call yang sama. Library tidak mengubah request body bisnis atau menambah retry. RestTemplate/RestClient dari builder Boot memakai instrumentasi Micrometer; gunakan `callHttp(dependency, operation, dto, action)` untuk scope logis INTERNAL di atas CLIENT span transport. `call` empat argumen membuat CLIENT span untuk SDK tanpa instrumentasi. Interceptor legacy tidak lagi dipasang otomatis; `propagation-hosts` hanya berlaku jika interceptor dipasang manual. [Detail tracing](TRACE-CONTEXT.md).

## Hasil verifikasi lokal

Suite library dan examples memeriksa operation payload, annotation parameter/result, allowlist field, SDK exported spans, root reuse, HTTP/W3C/async, masking, metrics sink gagal, job, transaksi, CRUD, dan flow kompleks. Lihat [kontrak enterprise](ENTERPRISE-LOGGING.md) serta test report Maven untuk hasil terbaru.

## Batas verifikasi

Contoh HTTP/job memakai fixture sintetis dan stub client, bukan repository insurance kantor. Servlet async completion dan error redispatch diuji dengan mock servlet lifecycle; belum uji container/network produksi, streaming, security chain kantor, WebFlux, atau arbitrary async services. Sink memakai kebijakan blocking/drop/retention backend existing; logging failure biasa ditahan tanpa raw fallback dan dihitung melalui LoggingHealth/Micrometer; status ERROR Logback dipantau terpisah. Budget performa dan rollout produksi menunggu consumer nyata.


## Dependency lifecycle terstruktur

Overload `dependencyLogging.call("s3", "s3.document.put", safeMetadataDto, action)` menghasilkan `DEPENDENCY_START/END/FAILED/REJECTED`, field dependency, span/parent span ID, durasi, dan classification dari bean ErrorClassifier. Exception asli tetap diteruskan tanpa stack trace tambahan pada wrapper. Overload lama tiga argumen tetap menghasilkan lifecycle PROCESS untuk kompatibilitas.

`TLog.info(step, message)` sekarang mempertahankan nama operation parent dan menulis langkah pada field `step`. Contoh lengkap: [flow klaim lintas sistem](../examples/insurance-crud/COMPLEX-FLOW.md).
