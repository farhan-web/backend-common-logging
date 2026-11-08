# Contoh Spring Boot CRUD dengan common logging

[Kontrak logging schema 3 dan migrasi](../../backend-common-logging/ENTERPRISE-LOGGING.md) · [Trace/span OpenTelemetry](../../backend-common-logging/TRACE-CONTEXT.md)

Project contoh yang memakai **dependency JAR** `io.github.commonlogging:backend-common-logging:0.1.0-SNAPSHOT`, bukan menyalin source logger ke aplikasi.

Stack: Java release **17**, Spring Boot **3.5.11**, Spring MVC, Bean Validation, Spring Data JPA, dan **H2 in-memory**. Build/test lokal memakai JDK 21 dengan target bytecode Java 17. Bisa dijalankan dengan Java 17+.

[Flow klaim kompleks: core + S3 + notifikasi](COMPLEX-FLOW.md) · [Library logging](../../backend-common-logging/README.md) · [Request siap jalan di IntelliJ](requests.http) · [Integration test](src/test/java/com/example/insurance/PolicyApiIntegrationTest.java)

## Jalankan

Dari root workspace:

```sh
./examples/insurance-crud/run.sh
```

Script mencari Maven di PATH atau Maven bawaan IntelliJ pada macOS ini, membuild JAR library lokal terlebih dahulu, membuild aplikasi yang mengarah ke JAR tersebut, lalu menjalankan executable JAR. Script tidak menjalankan tests; gunakan perintah test di bawah untuk verifikasi. Argumen Spring Boot dapat diteruskan, misalnya `./examples/insurance-crud/run.sh --server.port=8081`.

Alternatif dengan Maven dan Java di PATH:

```sh
mvn -f backend-common-logging/pom.xml package -DskipTests
mvn -f examples/insurance-crud/pom.xml package
java -jar examples/insurance-crud/target/insurance-crud-0.1.0-SNAPSHOT.jar
```

Aplikasi tersedia di `http://localhost:8080`, bind lokal `127.0.0.1`. Stop dengan Ctrl+C. Data **hilang saat proses aplikasi berhenti**; tidak ada seed otomatis. Endpoint list awalnya kosong.

Dependency logging sementara memakai JAR lokal langsung, tanpa install/publish ke repository Maven:

```xml
<dependency>
  <groupId>io.github.commonlogging</groupId>
  <artifactId>backend-common-logging</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>system</scope>
  <systemPath>${common-logging.jar}</systemPath>
</dependency>
```

Property `common-logging.jar` default mengarah ke `${project.basedir}/../../backend-common-logging/target/backend-common-logging-0.1.0-SNAPSHOT.jar`. Build library sekali sebelum reload Maven di IntelliJ atau menjalankan app dari module-nya. Spring Boot plugin memakai `includeSystemScope=true` supaya dependency ikut executable JAR. Setelah `clean` pada library, build ulang JAR sebelum menjalankan test/build aplikasi. Bila memakai file di lokasi lain, override `-Dcommon-logging.jar=/absolute/path/backend-common-logging.jar`.

Dari module aplikasi, sesudah JAR library tersedia:

```sh
cd examples/insurance-crud
mvn spring-boot:run
```

## Endpoint

| Method | URL | Hasil |
|---|---|---|
| POST | `/api/policies` | Create, 201 + Location |
| GET | `/api/policies` | List `{ "items": [...] }` |
| GET | `/api/policies/{id}` | Detail, 200 atau 404 |
| PUT | `/api/policies/{id}` | Ganti semua field, 200 atau 404/409 |
| DELETE | `/api/policies/{id}` | Delete, 204 atau 404 |

Nomor polis unik dan case-sensitive. Premium minimal 0.01, maksimal 12 digit integer dan 2 digit pecahan. Status `ACTIVE`/`INACTIVE`. Semua field request wajib. Ini contoh CRUD sederhana; belum ada autentikasi, pagination, optimistic locking, atau aturan bisnis polis kantor.

## Coba CRUD

```sh
curl -i -X POST http://localhost:8080/api/policies \
  -H 'Content-Type: application/json' \
  -d '{"policyNumber":"POL-2026-123456","holderName":"Budi Santoso","holderEmail":"budi@example.test","premium":1250000.50,"status":"ACTIVE"}'
```

Response mengandung ID hasil database serta data asli:

```json
{
  "id": 1,
  "policyNumber": "POL-2026-123456",
  "holderName": "Budi Santoso",
  "holderEmail": "budi@example.test",
  "premium": 1250000.50,
  "status": "ACTIVE"
}
```

Gunakan ID aktual dari response; angka 1 berikut mengasumsikan database baru:

```sh
curl http://localhost:8080/api/policies
curl http://localhost:8080/api/policies/1

curl -X PUT http://localhost:8080/api/policies/1 \
  -H 'Content-Type: application/json' \
  -d '{"policyNumber":"POL-2026-123456","holderName":"Siti Aminah","holderEmail":"siti@example.test","premium":1500000.00,"status":"INACTIVE"}'

curl -i -X DELETE http://localhost:8080/api/policies/1
curl -i http://localhost:8080/api/policies/1
```

## Contoh error

Kirim holderName kosong/email invalid/premium negatif untuk mendapat 400 dan daftar field/code aman. Nomor duplikat mendapat 409; ID yang tidak ada mendapat 404.

```json
{
  "code": "POLICY_NOT_FOUND",
  "message": "Policy was not found",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "fieldErrors": []
}
```

Malformed JSON, enum invalid, path ID invalid, dan method HTTP yang salah memakai status 4xx sesuai Spring, bukan disamarkan menjadi 500. Unexpected exception mendapat response 500 generik dan satu error log dengan stack frames. Tidak ada endpoint sengaja error pada executable app; probe NPE hanya berada di test source.

## Di mana dependency logging dipakai?

| File | Contoh |
|---|---|
| [pom.xml](pom.xml) | Dependency library biasa + starter AOP |
| [PolicyRequest](src/main/java/com/example/insurance/policy/PolicyRequest.java) | `@LogPayload`, `@LogValue`, `@LogMask`, strategi LAST_FOUR |
| [PolicyResponse](src/main/java/com/example/insurance/policy/PolicyResponse.java) | Masking response hanya untuk log |
| [PolicyService](src/main/java/com/example/insurance/policy/PolicyService.java) | `@LoggedOperation` untuk start/end/fail, `TLog.info` untuk langkah penting |
| [ApiErrorClassifier](src/main/java/com/example/insurance/error/ApiErrorClassifier.java) | Kode error aplikasi dipetakan untuk tExceptionLog |
| [GlobalExceptionHandler](src/main/java/com/example/insurance/error/GlobalExceptionHandler.java) | `ExceptionLoggingBridge` memanggil tExceptionLog; response dimiliki aplikasi |
| [application.yml](src/main/resources/application.yml) | H2, trace, application package, DTO capture paths |
| [logback-spring.xml](src/main/resources/logback-spring.xml) | StructuredLogEncoder Spring Boot; key/value native dan payload object JSON |

Auto-configuration library memasang request filter dan body advice; tidak perlu copy logger/filter ke aplikasi. Ambil X-Trace-ID dari response, lalu cari nilai traceId tersebut dalam JSON log untuk melihat request start, payload input, service start/info/end, payload response, dan request end. Class/method terlihat otomatis.

Payload yang sama di log menjadi:

```json
{
  "policyNumber": "***3456",
  "holderName": "***",
  "holderEmail": "***",
  "premium": 1250000.50,
  "status": "ACTIVE"
}
```

JSON response bisnis tetap utuh. `@LogExclude` dari library dapat dipakai bila nanti DTO memuat token/field yang sama sekali tidak boleh tampil; contoh CRUD ini tidak meminta credential. SQL logging dimatikan dan Hibernate constraint diagnostics tidak mencetak pesan SQL mentah; handler mencatat code yang aman.

## H2 Console

Buka `http://localhost:8080/h2-console` saat aplikasi sedang berjalan:

- JDBC URL: `jdbc:h2:mem:insurance`
- User: `sa`
- Password: kosong

Gunakan `SELECT * FROM INSURANCE_POLICIES;`. Console ini hanya untuk demo lokal, dengan remote console access nonaktif. H2 in-memory milik proses aplikasi tidak bisa dibuka dari proses database lain.

## Verifikasi

Dari root, `mvn test` menguji library dan consumer. `mvn -pl examples/insurance-crud -am test` memilih app beserta dependency source-nya. Delapan integration tests memakai database H2 asli: full CRUD, masking vs response, validation, duplicate, 404, conflicting update, malformed/type/HTTP input, serta unexpected NPE yang aman.


Hasil lokal: **20 integration tests aplikasi (8 CRUD + 12 flow kompleks) + 49 tests library lulus**, formatting check lulus, dan executable JAR berhasil dibuild. Bytecode aplikasi dan library bertarget Java 17; runtime pengujian lokal JDK 21.


## Flow integrasi kompleks

Contoh sekarang juga menyediakan [pengajuan klaim kompleks](COMPLEX-FLOW.md) dan [claims.http](claims.http): validasi polis lokal/core, AWS SDK upload ke simulator S3, submit core, state database, notifikasi, kompensasi, idempotency, dan reconciliation timeout. Simulator lokal otomatis hidup di port 9090; gunakan `--insurance.demo.port=9091` bila port terpakai. Core/AWS nyata tidak dipanggil secara default.


## Trace dan span

Setiap request tanpa upstream `traceparent` mendapat traceId baru. REQUEST_START dan REQUEST_END memiliki spanId root yang sama; service/dependency memiliki child span berbeda. Payload request/response terikat pada root request. Contoh request normal tidak mengirim ID statis. Request terakhir di [requests.http](requests.http) khusus mendemonstrasikan W3C traceparent dari upstream. Lihat [kontrak tracing dan migration](../../backend-common-logging/TRACE-CONTEXT.md).


## Payload pada PROCESS_START dan PROCESS_END

Method service yang memakai @LoggedOperation sekarang mencatat input DTO dan result DTO setelah masking, saat payload-enabled=true. Contoh policy.create: PROCESS_START.payload berisi PolicyRequest, PROCESS_END.payload berisi PolicyResponse termasuk ID yang baru disimpan; keduanya tetap memakai spanId service yang sama. REQUEST_PAYLOAD/RESPONSE_PAYLOAD tetap terkait span root HTTP. Claim submission menggunakan DTO request yang menjadi argument kedua, mengabaikan raw idempotency key, dan result wrapper Submission juga opt-in. Entity database yang tidak opt-in ditandai SKIPPED. Untuk menghindari pengulangan pada service tertentu gunakan logInput=false/logResult=false pada annotation.

## Revisi logging enterprise

Trace/span menggunakan SDK OpenTelemetry yang sama dengan Micrometer. HTTP client otomatis membuat CLIENT span di bawah scope integrasi `callHttp`; S3 yang belum diinstrumentasi memakai `call`. DTO baru perlu klasifikasi field; scalar method parameter dapat diberi `@LogValue`, `@LogMask`, `@LogExclude`. Contoh nyata: PolicyService.get, ClaimPolicyValidator.validate, ClaimSubmissionService.submit, dan DTO integration.

Local profile mengaktifkan masked payload dan native JSON. Library default/external profile menonaktifkan payload. OTLP export default nonaktif. Local metrics tersedia di `/actuator/metrics/common.logging.events.dropped` dan metric `common.logging.backend.errors`, `common.logging.payload.failures`, `common.logging.events.written`; external profile hanya membuka health.

Jalankan `./run.sh` dari folder ini, atau `./examples/insurance-crud/run.sh` dari root untuk membangun ulang library dan example. Maven command tetap `mvn spring-boot:run` dari folder example setelah library di-package; library bukan executable aplikasi. Detail [run lokal](../../backend-common-logging/ENTERPRISE-LOGGING.md).
