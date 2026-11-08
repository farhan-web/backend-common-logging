package com.example.insurance.integration;

import com.example.insurance.integration.IntegrationDtos.*;
import io.github.commonlogging.api.TLog;
import io.github.commonlogging.spring.DependencyLogging;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.exception.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class S3DocumentStorage {
  private final S3Client client;
  private final IntegrationProperties properties;
  private final DependencyLogging log;
  private final TLog tLog;

  public S3DocumentStorage(
      S3Client client, IntegrationProperties properties, DependencyLogging log, TLog tLog) {
    this.client = client;
    this.properties = properties;
    this.log = log;
    this.tLog = tLog;
  }

  public void put(String key, String contentType, byte[] bytes) {
    log.call(
        "s3",
        "s3.document.put",
        new DocumentMetadata(properties.s3().bucket(), key, contentType, bytes.length),
        () -> {
          try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .contentType(contentType)
                    .overrideConfiguration(trace())
                    .build(),
                RequestBody.fromBytes(bytes));
            return new ObjectReference(key);
          } catch (SdkException ex) {
            throw translate("S3_PUT", ex);
          }
        });
  }

  public void delete(String key) {
    log.call(
        "s3",
        "s3.document.delete",
        new ObjectReference(key),
        () -> {
          try {
            client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .overrideConfiguration(trace())
                    .build());
            return new ObjectReference(key);
          } catch (SdkException ex) {
            throw translate("S3_DELETE", ex);
          }
        });
  }

  private AwsRequestOverrideConfiguration trace() {
    var builder = AwsRequestOverrideConfiguration.builder();
    if (tLog.traceparent() != null) builder.putHeader("traceparent", tLog.traceparent());
    return builder.build();
  }

  private IntegrationException translate(String code, SdkException cause) {
    var kind =
        cause instanceof ApiCallTimeoutException || cause instanceof ApiCallAttemptTimeoutException
            ? IntegrationException.Kind.TIMEOUT
            : cause instanceof SdkClientException
                ? IntegrationException.Kind.UNAVAILABLE
                : IntegrationException.Kind.FAILURE;
    java.util.Set<Throwable> seen =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    for (Throwable e = cause; e != null && seen.add(e); e = e.getCause())
      if (e instanceof java.net.SocketTimeoutException) kind = IntegrationException.Kind.TIMEOUT;
    return new IntegrationException(code + "_" + kind.name(), kind, cause);
  }
}
