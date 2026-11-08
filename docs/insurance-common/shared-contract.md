# Kontrak bersama dan pendukung internal

> Kontrak aktif: [logging schema 3 dan panduan migrasi](../../backend-common-logging/ENTERPRISE-LOGGING.md), [OpenTelemetry trace/span](../../backend-common-logging/TRACE-CONTEXT.md). Rencana awal di bawah adalah konteks desain; perilaku aktif mengikuti kontrak tersebut.

[Plan utama](README.md) · [tLog](tlog.md) · [tExceptionLog](texception-log.md) · [Implementasi](implementation.md)

## Format event

Satu event menjadi satu object JSON memakai backend logging existing. Field umum:

| Field | Aturan |
|---|---|
| `schemaVersion`, `timestamp`, `level`, `event` | Versi 3, timestamp UTC ISO-8601, severity/event stabil |
| `application`, `environment`, `traceId` | Identitas aplikasi dan request/job |
| `operation`, `spanId`, `parentSpanId` | Label dan identitas invokasi |
| `className`, `methodName` | Lokasi aplikasi otomatis |
| `outcome`, `durationMs` | SUCCESS/REJECTED/FAILED/CANCELLED; durasi monotonic |
| `errorCategory`, `errorCode`, `exceptionType`, `errorOrigin`, `stackTrace` | Hanya bila relevan, sesuai ownership |
| `payload`, `payloadType`, `payloadStatus` | Structured payload yang sudah aman |
| `spanKind` | SERVER pada request, INTERNAL pada service, CLIENT pada dependency |

Tidak semua field wajib di setiap event. Metadata ekstra memakai allowlist. Span ID dan trace ID tidak dipakai sebagai label metrics ber-cardinality tinggi.

## Trace dan lokasi

Trace ID adalah 32 karakter hex lowercase nonzero; span ID 16 karakter hex lowercase nonzero. Ingress memakai W3C `traceparent`; header hilang/invalid membuat trace baru. Header `X-Trace-ID` dan `X-Span-ID` pada response hanya untuk diagnosis, bukan input pengendali trace. Root span request dibuat sebelum REQUEST_START, dipakai payload/start/end, dan menjadi parent service span. Trace ID berbeda dari claim ID/idempotency key. [Kontrak dan migrasi lengkap](../../backend-common-logging/TRACE-CONTEXT.md).

Aspect memakai target class/method aktual, bukan generated proxy. Manual logger mencari caller aplikasi dengan mekanisme sesuai versi Java, melewati helper internal. Gunakan metadata invocation bila tersedia agar tidak melakukan stack walking tanpa perlu. Sebelum routing, controller/method boleh belum diketahui; jangan mengarang lokasi. Async context harus dicopy saat submit dan dipulihkan saat selesai. MDC adalah output context, bukan jaminan propagation untuk semua model concurrency.

## Annotation payload DTO

```java
@LogPayload
public class ClaimRequest {
    @LogValue private String productCode;
    @LogMask
    private String participantName;
    @LogMask(strategy = MaskStrategy.LAST_FOUR)
    private String policyNumber;
    @LogExclude
    private String accessToken;
}
```

- `@LogPayload`: opt-in DTO yang telah direview. Boundary logging juga harus aktif.
- `@LogMask`: default FULL menjadi placeholder `***`; null tetap null.
- `LAST_FOUR`: hanya string identifier yang boleh ditampilkan sebagian; panjang ≤ 4 dimask seluruhnya. Unsupported type dimask penuh/skip, tidak dibuka mentah.
- `@LogExclude`: field tidak muncul; menang terhadap annotation lain. Konflik getter/field/inheritance memilih kebijakan lebih ketat.
- Field DTO tanpa aturan dilewati. @LogValue menyetujui field aman, @LogMask menutup nilai, @LogExclude menghapusnya. Annotation tidak otomatis mendeteksi PII.
- Nested DTO harus opt-in atau punya policy eksplisit. Collection memproses setiap item. Map/raw JSON/external DTO memakai field/path policy terdaftar atau di-skip.

Serializer khusus logging tidak mengubah DTO, tidak memasang masking pada global HTTP mapper, dan tidak menggunakan raw toString sebagai fallback. Dukungan record/getter/field/inheritance mengikuti style target dan diuji. Sanitized snapshot dibuat pada boundary, bukan menyimpan DTO mutable untuk diserialisasi belakangan.

## Batas dan keamanan output

Batasi panjang, jumlah item, kedalaman, siklus, dan ukuran payload/stack trace. Batasi traversal sebelum materialisasi penuh. `payloadStatus` adalah CAPTURED/TRUNCATED/SKIPPED/FAILED. Truncation tetap menghasilkan JSON valid, bukan memotong string JSON di tengah token. Error sanitasi menghasilkan metadata fallback aman tanpa raw object.

Token, credential, data identitas/kesehatan mengikuti policy kantor. Annotation DTO tidak membersihkan exception message, URL query, atau stack trace; jalur itu tetap disanitasi. Binary/file/stream cukup metadata aman tanpa membaca stream demi logging. Malformed request tanpa DTO tidak dicatat raw. Jangan mengasumsikan regex cukup menyaring seluruh arbitrary payload.

## Konfigurasi

Konfigurasi konseptual mencakup enable log/payload, boundary aktif, trace header, batas ukuran, dan policy external DTO. Library baru tidak membuka semua payload; pilot mengaktifkan boundary yang direview. Payload switch tidak menghilangkan lifecycle/error log; emission switch tidak mengubah cleanup/response handling. Logging sink/root level existing dihormati tanpa appender duplikat.

Validasi konfigurasi saat startup. Logging/formatting failure biasa tidak mengganti hasil bisnis; tidak ada janji recovery dari resource exhaustion fatal. Volume, sink blocking/drop, retention, dan akses mengikuti platform kantor.
