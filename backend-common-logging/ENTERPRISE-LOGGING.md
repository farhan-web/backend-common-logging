# Kontrak payload dan event — schema 3

[Library](README.md) · [Java 8 / Boot 2.4: build dan migrasi](JAVA8-COMPATIBILITY.md) · [Trace/span](TRACE-CONTEXT.md) · [Example](../examples/insurance-crud/README.md)

Baseline aktif: **satu JAR Java 8 untuk Spring Boot 2.4.3/2.4.4**. API bisnis tetap dua facade: TLog dan TExceptionLog. Source dan dependencies legacy disesuaikan tanpa menggandakan artifact. DTO record pada implementasi sebelumnya menjadi class immutable dengan accessor lama dan JavaBean getter.

## Tracing dan adapter legacy

SDK OpenTelemetry 1.49.0 tetap mengelola span, ID, W3C propagation dan export hooks. Spring Boot 2.4 menggunakan filter javax.servlet dan RestTemplate interceptor library; tidak memakai Micrometer Observation, RestClient atau tracing bridge Boot 3. Kontrak detail ada di [trace/span](TRACE-CONTEXT.md).

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

## JSON pada SLF4J 1.7 / Logback

Sink default menulis sanitized JSON message pada logger `common.logging`. Dedicated appender dengan `%msg%n` dan `additivity=false` menghasilkan satu object JSON per baris tanpa escaped JSON di dalam message. Lihat [konfigurasi example](../examples/insurance-crud/src/main/resources/logback-spring.xml). Jangan menggunakan StructuredLogEncoder khusus Boot 3 atau membungkus pesan tersebut lagi sebagai string pada JSON encoder lain. Untuk platform kantor, override EventSink jika perlu memetakan event ke sink existing.

Field severity, timestamp, trace/span, class/method, outcome dan payload berada di object schemaVersion 3. Property lama structured-arguments-enabled tidak berpengaruh pada baseline Boot 2; output selalu JSON message. Annotation field tetap explicit allowlist. Dashboard/parser harus menerima field yang dilewati dan envelope arguments.

## Kegagalan logging dapat dimonitor

`LoggingHealth` menjadi MeterBinder dengan counter tanpa tag traceId/spanId/payload:

| Metric | Arti |
|---|---|
| `common.logging.events.written` | Event diterima sink tanpa exception; bukan ACK penyimpanan collector |
| `common.logging.events.dropped` | Kegagalan serialisasi event/sink yang terlihat oleh library |
| `common.logging.payload.failures` | Serializer payload mengembalikan FAILED |
| `common.logging.backend.errors` | Status ERROR yang dilaporkan Logback setelah monitor dipasang |

Status backend bisa mencakup logger aplikasi lain dan tidak selalu sama dengan jumlah event hilang. Error collector/disk/network yang tidak dilaporkan sink/Logback harus dimonitor platform logging. Error awal sebelum Spring memasang monitor tetap dibaca dari status startup. Logging tidak melempar ulang kegagalan sink ke proses bisnis atau mencatat payload mentah sebagai fallback.

Local example membuka health/metrics di loopback, external profile hanya health. SDK fallback mengambil bean SpanProcessor aplikasi untuk export; collector tidak dihubungi otomatis. Aplikasi dapat menyediakan bean OpenTelemetry sendiri. SDK standalone memakai parent-based sampler always-on untuk pengujian; sampling produksi dikonfigurasi melalui SDK aplikasi. Logs tetap dapat ditulis untuk request unsampled.

## Verifikasi dan batas

Matriks memakai JDK 8 nyata: **60 test library dan 21 test consumer per versi Boot**, tanpa failure/error/skipped. Checksum membuktikan consumer 2.4.3 dan 2.4.4 memakai satu file JAR yang sama. Suite memeriksa field baru tanpa aturan, generic/interface masking, parameter/result, lifecycle, context cleanup, fail-safe sink metrics, exported SDK spans, servlet HTTP nyata, JSON encoder, transaksi, CRUD, timeout/reconciliation/compensation.

Belum diverifikasi: repository/core/S3/collector kantor, security chain, beban, backpressure, retention, servlet async produksi dan arbitrary async services. Annotation common tidak membersihkan logs/telemetry SDK lain secara otomatis. Pilot produksi tetap diperlukan. Reuse di Java 21 dan migrasi ke Boot 3 adalah verifikasi framework terpisah.

Referensi: [Spring Boot 2.4](https://docs.spring.io/spring-boot/docs/2.4.4/reference/htmlsingle/), [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java), [OWASP logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html). Annotation payload adalah kontrak library ini, bukan annotation standar tracing.
