# Satu JAR untuk Java 8 dan Spring Boot 2.4

[Library](README.md) · [Trace/span](TRACE-CONTEXT.md) · [Payload dan event](ENTERPRISE-LOGGING.md) · [Example](../examples/insurance-crud/README.md)

Kontrak aktif: satu artifact `io.github.commonlogging:backend-common-logging:0.1.0-SNAPSHOT`, plain JAR Java 8 untuk Spring Boot **2.4.3 dan 2.4.4**. Examples adalah referensi/test consumer dan tidak dibundel dalam library. Tidak ada artifact terpisah per versi Java atau Boot.

## Build library

Gunakan **JDK 8 dan Maven 3.6.3+ versi 3.x**. Build dan test memeriksa runtime Java 8 yang sebenarnya, bukan hanya compiler target.

```bash
# macOS; pada OS lain arahkan JAVA_HOME ke instalasi JDK 8.
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version

# Dari root repository:
bash scripts/build-java8.sh
```

Hasil: `backend-common-logging/target/backend-common-logging-0.1.0-SNAPSHOT.jar`.

`MAVEN_BIN` dapat menunjuk executable Maven 3 jika tidak tersedia di PATH. Script dapat menemukan JDK 8 dan bundled Maven IntelliJ di macOS ketika belum dipilih. JAVA_HOME yang sudah diset tidak ditimpa. Formatter memerlukan JDK 17+ dan hanya aktif pada profile `format`; build/runtime library tetap JDK 8.

## Verifikasi satu binary di dua runtime Boot

```bash
bash scripts/verify-java8.sh
```

Script membangun library sekali pada baseline 2.4.3, menjalankan unit/integration test library, lalu menjalankan consumer example pada 2.4.3 dan 2.4.4 dengan file JAR tersebut. Checksum JAR harus tetap sama. Test juga memeriksa versi Boot aktual, auto-configuration `spring.factories`, serta class-file version maksimal 52 (Java 8) pada API library dan SDK tracing.

POM mengimpor BOM Boot melalui `${spring-boot.version}` (default 2.4.3), sehingga `-Dspring-boot.version=2.4.4` memilih dependency runtime yang sebenarnya. Tidak mewarisi parent Boot yang mengunci dependency ke baseline lain. Ini pilihan matriks verifikasi, bukan varian artifact baru.

## Memasang di aplikasi kantor

Cara yang disarankan untuk development lokal: install artifact **beserta POM-nya** supaya dependency runtime ikut di-resolve.

```bash
mvn -f backend-common-logging/pom.xml install -DskipTests
```

Di aplikasi Boot 2.4.3/2.4.4:

```xml
<dependency>
  <groupId>io.github.commonlogging</groupId>
  <artifactId>backend-common-logging</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Aplikasi menyediakan starter web untuk MVC/filter dan opsional actuator untuk endpoint metrics. Dependency Spring bersifat optional pada library; versi framework aplikasi tetap mengikuti BOM milik aplikasi. Library membawa dependency SDK OpenTelemetry 1.49.0, Jackson databind, SLF4J API, dan Micrometer core melalui POM.

Jika tetap memakai JAR langsung dengan `systemPath`, Maven **tidak** membawa dependency transitif. Example mempertahankan cara ini sebagai referensi dan mendeklarasikan dependency runtime secara eksplisit. Minimal pastikan web/AOP, Jackson, SLF4J, Micrometer core dan `opentelemetry-sdk:1.49.0` tersedia. Menyalin JAR library saja bukan pengganti seluruh dependency. JAR tidak mengemas Spring/framework atau contoh aplikasi menjadi fat JAR.

## Konfigurasi

```yaml
backend:
  common:
    logging:
      application: claims-api
      environment: development
      application-package: com.company.insurance
      payload-enabled: false
      payload-paths:
        - /api/claims
        - /api/claims/*
      propagation-hosts:
        - core-insurance.internal
```

Auto-configuration ditemukan melalui `META-INF/spring.factories`, tidak memerlukan component scan package library. Servlet memakai `javax.servlet`. Payload default nonaktif; aktifkan setelah DTO dan boundary direview. Library tidak memasang response mapper/global exception handler bisnis.

API facade tetap `TLog` dan `TExceptionLog`; accessor lama seperti `context.traceId()` dan `result.status()` tetap tersedia. Record diganti class immutable Java 8 dengan constructor dan JavaBean getters. Annotation parameter/field, masking, size limits, lifecycle, failure ownership dan schemaVersion 3 dipertahankan.

## Tracing dan HTTP

OpenTelemetry SDK tetap membuat ID/span dan memvalidasi W3C `traceparent`. Header masuk valid diteruskan sebagai parent; jika tidak valid/tidak ada dibuat root baru. Filter menggunakan SDK span yang sudah aktif, atau memiliki satu SERVER span sendiri. Diagnostic response headers: `X-Trace-ID`, `X-Span-ID`.

Boot 2.4 tidak menyediakan Micrometer Observation/tracing bridge yang dipakai implementasi Boot 3 sebelumnya. Library sekarang memasang `TraceClientInterceptor` pada RestTemplate yang dibuat dengan **RestTemplateBuilder bean Spring Boot**. Untuk host allowlist, interceptor membuat SDK CLIENT span dan mengirim context span itu; jika scope aktif sudah CLIENT, span tersebut dipakai ulang. Host di luar allowlist tidak mendapat header baru dari library. Tidak mencatat URL lengkap, query, body, atau exception message pada transport span. Client span mencakup eksekusi sampai response headers tersedia; bukan lifecycle streaming body.

```java
return dependencyLogging.callHttp("core", "core.lookup", safeRequest,
    () -> restTemplate.postForObject("/lookup", safeRequest, LookupResponse.class));
```

Scope logis `callHttp` INTERNAL; interceptor memiliki CLIENT transport span. RestTemplate yang dibuat manual harus memasang interceptor secara eksplisit. SDK tanpa instrumentasi, seperti contoh S3, memakai `call` empat argumen dan `tLog.traceparent()` pada request override. Hindari menumpuk instrumentasi tambahan pada client yang sama. Sleuth/Brave, Java agent atau sistem tracing lain di kantor perlu diperiksa sebelum digabung; tidak diasumsikan otomatis berbagi context.

Spring memakai satu bean OpenTelemetry. Aplikasi dapat menyediakan bean sendiri atau bean `SpanProcessor` (misalnya BatchSpanProcessor + exporter) yang diambil SDK fallback library. Tidak ada collector/export jaringan yang otomatis diaktifkan. Exporter/framework lain memiliki kebijakan data sendiri; annotation masking hanya mengatur serializer library.

## JSON dan metrics pada Boot 2.4

SLF4J 1.7 menerima **sanitized JSON message** pada logger `common.logging`. Appender khusus `%msg%n`, `additivity=false`, mempertahankan satu objek JSON per baris; lihat [Logback example](../examples/insurance-crud/src/main/resources/logback-spring.xml). Jangan memakai StructuredLogEncoder khusus Boot 3 atau mengemas JSON ini lagi sebagai string message di encoder JSON lain. Sink dapat di-override melalui bean EventSink sesuai platform kantor. Property lama `structured-arguments-enabled` dipertahankan sebagai compatibility setting tanpa pengaruh; Boot 2 selalu memakai JSON message.

Counter Micrometer: `common.logging.events.written`, `common.logging.events.dropped`, `common.logging.payload.failures`, `common.logging.backend.errors`. Written berarti sink menerima event, bukan bukti collector menyimpannya. Monitor Logback menghitung status ERROR backend setelah listener terpasang; bukan hitungan persis semua event yang hilang. Kegagalan logging yang tertangkap tidak mengganti hasil bisnis dan tidak memakai raw fallback.

## Example dan migrasi mendatang

```bash
bash examples/insurance-crud/run.sh
```

Example kini memakai Boot 2.4, Java 8, H2, RestTemplate dan AWS SDK v2 dengan Apache HTTP client. Simulator menutup koneksi setelah response untuk menjaga determinisme setelah timeout/cancellation pada JDK 8. Parameter controller memakai `@Validated` dan handler validation legacy agar invalid parameter tetap 400 tanpa raw rejected value/stack trace yang tidak perlu.

JAR bytecode Java 8 dapat menjadi dasar reuse pada runtime Java yang lebih baru; dukungan integrasi framework harus diuji terpisah. **Boot 2.4 bukan target runtime Java 21 yang diverifikasi**. Saat migrasi sekaligus ke Boot 3, servlet `javax` dan registrasi/integrasi Spring perlu disesuaikan. Tidak ada klaim satu adapter legacy ini langsung kompatibel Boot 3.

Verifikasi lokal memakai fixture sintetis dan simulator, bukan core insurance/S3/collector kantor. Security chain, retention, backpressure dan uji beban kantor tetap bagian pilot pada repository target.
