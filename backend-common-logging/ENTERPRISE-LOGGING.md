# Kontrak logging schema 3

[Library](README.md) · [Trace/span](TRACE-CONTEXT.md) · [Contoh CRUD](../examples/insurance-crud/README.md) · [Flow kompleks](../examples/insurance-crud/COMPLEX-FLOW.md) · [Desain awal](../docs/insurance-common/README.md)

Dokumen ini menjadi kontrak aktif untuk revisi enterprise. API bisnis tetap dua facade: `TLog` dan `TExceptionLog`. Baseline: Java release 17, Spring Boot 3.5.11, MVC synchronous. Build/test lokal menggunakan JDK 21; belum menjadi sertifikasi deployment JDK 17 atau repository kantor.

## Trace yang sama dengan framework

Library menggunakan OpenTelemetry SDK, bukan generator ID/parser W3C sendiri. Pada Spring Boot, `TracingRuntime` memakai bean `OpenTelemetry` yang juga digunakan Micrometer bridge. Operasi INTERNAL membuka Micrometer Observation agar HTTP client otomatis mengenali parent operasi. Di luar Spring tersedia SDK fallback tanpa exporter dan tanpa global registration.

Consumer Spring menyediakan dependency berikut, versi mengikuti BOM Boot:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
<dependency><groupId>io.micrometer</groupId><artifactId>micrometer-tracing-bridge-otel</artifactId></dependency>
<!-- Jika akan mengirim trace ke collector: -->
<dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-exporter-otlp</artifactId></dependency>
```

Examples tetap memakai `systemPath` ke JAR lokal sesuai kebutuhan development. Karena system dependency tidak membawa dependency transitif, dependencies di atas ditulis eksplisit pada POM example. Rebuild library sebelum menjalankan example. Untuk distribusi kantor gunakan repository artifact/internal Maven setelah koordinat ditentukan.

`TraceFilter` menggunakan SERVER span framework yang sudah aktif; jika belum ada, filter membuat dan memiliki SERVER span sendiri. Filter hanya mengakhiri span yang dibuatnya sendiri. Tidak ada dua SERVER span library/framework untuk request yang sama.

Untuk Boot RestClient/RestTemplate builder gunakan `dependencyLogging.callHttp("core", "core.lookup", dto, action)`: event DEPENDENCY berada di scope INTERNAL; Boot membuat CLIENT span untuk transport sebenarnya dan mengirim W3C context. Untuk SDK yang belum diinstrumentasi seperti S3 pada contoh gunakan `call(...)` empat argumen: wrapper membuat CLIENT span dan adapter memasang `tLog.traceparent()` pada request. Hindari wrapper CLIENT tambahan jika SDK di kantor sudah diinstrumentasi.

Automatic Boot propagation mengikuti konfigurasi framework dan destination client, bukan `propagation-hosts` milik library. Property lama tersebut hanya dipakai oleh `TraceClientInterceptor` bila dipasang manual; interceptor itu tidak lagi otomatis dipasang. Tetapkan endpoint dan kebijakan egress di aplikasi. Standalone/manual interceptor tidak membuat span transport tambahan.

## Payload opt-in per field dan parameter

```java
@LogPayload
record ClaimInput(
    @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogValue BigDecimal amount,
    @LogExclude String documentBase64,
    String unreviewedNewField) {}

@LoggedOperation("claim.check")
@LogValue // scalar result boleh dicatat; tetap perlu global payload-enabled
public boolean check(
    @LogMask(strategy = MaskStrategy.LAST_FOUR) String policyNumber,
    @LogValue("eligible") boolean eligible,
    @LogExclude String accessToken,
    ClaimInput request) {
    tLog.info("claim.check.rules", "Eligibility rules completed");
    return eligible;
}
```

- `@LogPayload` menyetujui tipe DTO; setiap property harus memakai `@LogValue`, `@LogMask`, atau external allowlist policy. Field tanpa aturan dilewati. `unreviewedNewField` pada contoh tidak muncul.
- `@LogValue` menyetujui nilai yang telah dinilai aman. Annotation bukan detektor PII. Letakkan pada record component, field/getter, parameter, atau method untuk scalar result.
- `@LogMask` default FULL (`***`), LAST_FOUR hanya menampilkan suffix string panjang >4. Object/angka yang dimask sebagian tetap ditutup penuh. Method annotation dapat memasking result secara penuh.
- `@LogExclude` menghapus property/parameter; pada method menonaktifkan payload result. Aturan interface/superclass yang lebih ketat tidak boleh dilonggarkan implementasi. Urutan: EXCLUDE, FULL, LAST_FOUR, NONE.
- Parameter scalar tanpa annotation dilewati. Parameter DTO opt-in tetap otomatis dipilih. Satu DTO ditulis langsung; kombinasi beberapa DTO/scalar yang dipilih memakai `payload.arguments` berisi `name` dan `value`. Gunakan compiler `-parameters`; `@LogValue("nama")` memberi nama eksplisit.
- Return entity/arbitrary object tetap tidak di-dump meskipun `@LogValue`; serializer tidak mengeksekusi custom serializer bisnis. Getter pada field yang disetujui tetap dapat dieksekusi, jadi gunakan DTO data sederhana.
- DTO/output HTTP asli tidak berubah. Tidak ada fallback raw body/string/header/token atau exception message pada event common. Instrumentasi framework/SDK lain dapat menyimpan URL atau exception event pada telemetry-nya sendiri; audit/redaksi exporter tetap diperlukan sebelum export produksi. Annotation DTO hanya berlaku pada serializer common.
- Batas depth, byte, item, node dan panjang string tetap berlaku. Daftar parameter yang panjang tidak mengubah signature atau nilai bisnis.

`@LoggedOperation(logInput=false, logResult=false)` mematikan capture per method. Global `payload-enabled=false` mengalahkan semua annotation. Default library false, local example true, external example false. HTTP path allowlist mengatur body advice, bukan method/service annotation. Review boundary agar payload tidak berulang pada setiap method kecil.

Aspect dipasang pada method implementasi Spring bean; self-invocation, private method dan instance yang dibuat manual tidak melewati proxy. Future/reactive/@Async dilewati aspect synchronous. Propagasi executor harus eksplisit atau memakai mekanisme context propagation platform kantor.

## JSON terstruktur tanpa JSON di dalam message

Mode kompatibilitas default (`structured-arguments-enabled=false`) tetap mengirim JSON message ke `common.logging`. Pilih mode native untuk encoder Spring Boot:

```yaml
backend:
  common:
    logging:
      structured-arguments-enabled: true
      payload-enabled: false
logging:
  structured:
    format:
      console: logstash
```

Mode native memakai SLF4J 2 key/value dan message berisi safe message pemanggil atau `common.logging.event`; payload berupa object JSON, bukan escaped string. Encoder menulis severity/message; library menghindari duplikasi field severity, message, dan MDC. Jika memakai custom Logback XML, gunakan `StructuredLogEncoder` seperti [example](../examples/insurance-crud/src/main/resources/logback-spring.xml), bukan `%msg` atau pattern console biasa. Pilih satu formatter; aktifkan flag native hanya jika appender mempertahankan key/value.

Schema berubah menjadi `schemaVersion: 3` karena field payload sekarang explicit allowlist. Dashboard/parser lama perlu menerima field yang hilang dan envelope arguments. Nama event lifecycle tetap dipertahankan.

## Kegagalan logging dapat dimonitor

`LoggingHealth` menjadi MeterBinder dengan counter tanpa tag traceId/spanId/payload:

| Metric | Arti |
|---|---|
| `common.logging.events.written` | Event diterima sink tanpa exception; bukan ACK penyimpanan collector |
| `common.logging.events.dropped` | Kegagalan serialisasi event/sink yang terlihat oleh library |
| `common.logging.payload.failures` | Serializer payload mengembalikan FAILED |
| `common.logging.backend.errors` | Status ERROR yang dilaporkan Logback setelah monitor dipasang |

Status backend bisa mencakup logger aplikasi lain dan tidak selalu sama dengan jumlah event hilang. Error collector/disk/network yang tidak dilaporkan sink/Logback harus dimonitor platform logging. Error awal sebelum Spring memasang monitor tetap dibaca dari status startup. Logging tidak melempar ulang kegagalan sink ke proses bisnis atau mencatat payload mentah sebagai fallback.

Local example mengekspos health/metrics di loopback. External profile hanya mengekspos health. Tentukan security, akses log, retention, alert counter, collector dan backpressure di platform kantor. Tidak ada pengiriman OTLP secara default; aktifkan `OTEL_EXPORT_ENABLED=true` dan `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` pada external profile setelah collector disiapkan. `sampling.probability=1.0` di contoh untuk verifikasi; tentukan sampling produksi sesuai volume dan kebutuhan investigasi. Logs tetap ditulis untuk request unsampled.

## Verifikasi dan batas

Suite mencakup privacy default field baru, masking interface/parameter/result, banyak parameter scalar, lifecycle/input/result, scope cleanup, fail-safe sink metrics, exported SDK spans, satu root server, W3C invalid input, transaksi, CRUD H2, HTTP dan AWS SDK menuju simulator lokal, timeout/reconciliation/compensation, serta encoder JSON aktual dengan deteksi key duplikat.

Belum dilakukan: koneksi core/S3 kantor, external collector, uji beban representatif, security chain kantor, container servlet async produksi, WebFlux dan arbitrary async service. Sebelum rollout, pilot pada satu flow di repository kantor: cocokkan trace di collector dengan log, ukur p95/p99 dan volume dengan payload on/off, simulasi sink gagal, uji tidak ada PII di semua appender, lalu tetapkan retention dan akses. Revisi kode ini tidak mengubah otorisasi atau mengakses sistem kantor.

Referensi: [Spring Boot tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html), [Spring Boot structured logging](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html), [Micrometer tracing](https://docs.micrometer.io/tracing/reference/api.html), [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/instrumentation/), [OWASP logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html). Annotation payload merupakan kontrak library ini, bukan annotation standar OpenTelemetry.

Hasil lokal revisi ini: test library dan examples lulus tanpa failure/error/skipped; test server servlet nyata membuktikan satu framework SERVER span dan response span ID cocok dengan span yang diekspor. Lihat report Maven untuk jumlah terbaru.
