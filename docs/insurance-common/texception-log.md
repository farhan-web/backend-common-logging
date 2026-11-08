# Komponen tExceptionLog

> Kontrak aktif: [logging schema 3 dan panduan migrasi](../../backend-common-logging/ENTERPRISE-LOGGING.md), [OpenTelemetry trace/span](../../backend-common-logging/TRACE-CONTEXT.md). Rencana awal di bawah adalah konteks desain; perilaku aktif mengikuti kontrak tersebut.

[Plan utama](README.md) · [tLog](tlog.md) · [Kontrak bersama](shared-contract.md) · [Implementasi](implementation.md)

## Tujuan dan API konseptual

```java
tExceptionLog.error("claim.submit", exception);
```

Mencatat kegagalan dengan trace ID, operasi, lokasi, kategori, safe message, original cause chain, dan stack trace bila dibutuhkan. API final dapat menerima operation token/context agar kegagalan ditautkan ke start yang benar. Call manual mengambil lokasi caller aplikasi otomatis; log dari handler menerima context operasi gagal, bukan berpura-pura handler adalah lokasi throw.

## Kategori

| Kondisi | Perilaku |
|---|---|
| Input validation/business rejection | Metadata/code aman, INFO/WARN, umumnya tanpa stack trace; dapat memakai tLog rejection |
| Dependency timeout/unavailable/invalid response | Kategori terpisah, target dependency dan cause terjaga |
| Unexpected exception seperti NPE | ERROR, safe message, cause chain/stack trace untuk diagnosis |

Classifier dapat memakai exception aplikasi existing. Tidak wajib membuat hierarchy exception baru. Jika adapter menerjemahkan exception, pertahankan cause; kode bisnis dan pemetaan HTTP dimiliki aplikasi.

## Satu pemilik stack trace

Handler HTTP/job mencatat stack trace untuk failure yang mencapai boundary-nya. Layer yang meneruskan exception hanya mencatat metadata kegagalan bila diperlukan. Recovery lokal yang tidak meneruskan exception menjadi pemilik log failure/recovery tersebut. Hindari catch-log-rethrow dengan stack trace di setiap layer.

Metadata failure operasi dapat muncul pada beberapa boundary, tetapi stack trace final dicatat sekali. tExceptionLog tidak mengandalkan global identity cache atau flag pada exception untuk dedup; ownership ditentukan oleh integrasi aplikasi.

## Lokasi error

`className`/`methodName` menjelaskan lokasi proses/log. `errorOrigin` menjelaskan frame aplikasi yang relevan pada cause chain: class, method, file, dan line jika tersedia. Jangan selalu memakai frame wrapper teratas. Bila origin tidak dapat dipastikan, hilangkan field terstruktur dan pertahankan stack trace sebagai bukti. Handler harus menerima failure context sebelum operation scope dibersihkan.

## Try-catch dan response

Tangkap exception bila bisa recover atau menerjemahkan makna; jangan return null/string kosong/sukses untuk menutupi kegagalan. Original exception tetap diteruskan. Cancellation/interruption memerlukan handling sesuai jenisnya; jangan menangkap seluruh Throwable sebagai error biasa.

Komponen ini tidak membentuk HTTP response. Global exception handler existing boleh memanggilnya, lalu mempertahankan response/status/kode existing. Filter/security/job failure ditangani boundary masing-masing karena tidak semuanya melewati controller advice. Jangan mengirim stack trace atau pesan mentah dependency ke client.

## Payload saat error dan acceptance

Hubungkan dengan request payload yang sudah dicatat memakai trace ID; jangan mengulang payload pada setiap exception. DTO yang gagal validation dapat dicatat tersanitasi jika tersedia. Malformed JSON tanpa DTO utuh cukup metadata, tanpa dump raw body.

Uji NPE dengan file/baris, translated exception dengan cause asli, validation rejection, timeout, handler di luar MVC, recovered failure, dan sanitasi message/stack trace. Error logging tidak boleh menutupi original exception atau mengubah response bisnis.
