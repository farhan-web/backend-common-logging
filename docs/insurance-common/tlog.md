# Komponen tLog

> Kontrak aktif: [logging schema 3 dan panduan migrasi](../../backend-common-logging/ENTERPRISE-LOGGING.md), [OpenTelemetry trace/span](../../backend-common-logging/TRACE-CONTEXT.md). Rencana awal di bawah adalah konteks desain; perilaku aktif mengikuti kontrak tersebut.

[Plan utama](README.md) · [Komponen error](texception-log.md) · [Kontrak bersama](shared-contract.md) · [Implementasi](implementation.md)

## Tujuan

Mencatat proses mulai, langkah penting, hasil validasi, dan proses selesai. Pemanggilan berbeda tetap memakai komponen yang sama, seperti activity tLog/tLog1 pada BWCE.

## API konseptual

```java
try (LogOperation operation = tLog.start("claim.submit", request)) {
    try {
        tLog.info("claim.validation", "Validation passed");
        ClaimResponse response = claimService.submit(request);
        operation.end(response);
        return response;
    } catch (RuntimeException ex) {
        operation.fail(ex); // metadata saja; stack trace dimiliki handler
        throw ex;
    }
}
```

Ini ilustrasi kontrak, bukan API yang sudah tersedia. `start` menghasilkan operation scope/token agar start/end berpasangan, durasi tepat, dan nested operation tidak menimpa context parent. Nama operasi adalah label bisnis opsional yang stabil; class/method diambil otomatis. Jika aspect mengelola lifecycle, aplikasi tidak menulis start/end manual untuk operasi yang sama.

## Event dan perilaku

| Pemanggilan/kejadian | Event | Isi |
|---|---|---|
| Start | `PROCESS_START` | Trace, span ID, lokasi, payload input tersanitasi jika diberikan |
| Info | `PROCESS_INFO` | Step/message dan metadata aman |
| Expected validation/business rejection | `PROCESS_REJECTED` | Code dan nama field aman; tanpa rejected value/stack trace default |
| End sukses | `PROCESS_END` | Outcome, durasi, payload output tersanitasi jika diberikan |
| Scope ditutup tanpa end/failure/rejection | `PROCESS_INCOMPLETE` | Diagnostic tanpa klaim sukses; context tetap dibersihkan |

Normal event memakai INFO. Expected rejection memakai INFO atau WARN sesuai kebutuhan operasional. `tExceptionLog` mencatat failure; scope terkait ditandai gagal melalui token/context agar tidak menghasilkan incomplete tambahan. Penandaan ini bagian lifecycle internal, bukan mutable flag pada exception.

## Payload

Input/output DTO mengikuti [annotation masking](shared-contract.md). Aspect @LoggedOperation mencatat input DTO opt-in pada PROCESS_START dan result termasking pada PROCESS_END, selain payload boundary HTTP/dependency. Default logInput/logResult true dengan global payload-enabled tetap sebagai master switch; metode dapat mematikannya untuk mengurangi pengulangan. Multiple DTO diberi nama parameter; raw scalar/credential dan entity tak dikenal tidak di-dump. Method void tidak mempunyai result payload, sedangkan null result dicatat sebagai null. Tipe non-opt-in ditandai SKIPPED.

Payload masuk hanya tersedia setelah deserialization; request transport start dapat lebih dahulu. Jika memakai body advice, payload bisa berupa event `REQUEST_PAYLOAD`/`RESPONSE_PAYLOAD` terpisah dengan trace ID sama. Jangan menunda request start sampai DTO tersedia.

## Batas tanggung jawab

Tidak menelan exception, memilih HTTP status, menambah retry, atau mengubah transaksi. Token terminal hanya sekali; cleanup idempotent dan selalu memulihkan parent context. End berarti boundary yang diukur selesai, bukan otomatis berarti commit database atau pengiriman response berhasil.

## Acceptance

Start/end membawa span ID sama; nested/repeated calls terbedakan; durasi monotonic; class/method menunjuk aplikasi; payload termasking; scope cleanup tetap berjalan pada failure; terminal tidak ganda. Async completion harus diintegrasikan secara khusus, bukan dianggap selesai ketika future dikembalikan.
