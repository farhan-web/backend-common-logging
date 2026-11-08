package io.github.commonlogging.spring;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("backend.common.logging")
public class LoggingProperties {
  private boolean structuredArgumentsEnabled;

  public boolean isStructuredArgumentsEnabled() {
    return structuredArgumentsEnabled;
  }

  public void setStructuredArgumentsEnabled(boolean enabled) {
    structuredArgumentsEnabled = enabled;
  }

  private boolean enabled = true,
      payloadEnabled = false,
      webEnabled = true,
      operationEnabled = true;
  private String application = "application",
      environment = "unknown",
      applicationPackage = "",
      traceIdHeader = "X-Trace-ID";
  private int maxDepth = 6,
      maxItems = 50,
      maxString = 1024,
      maxPayloadBytes = 16384,
      maxFrames = 64;
  private List<String> payloadPaths = new ArrayList<>();
  private List<String> propagationHosts = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public boolean isPayloadEnabled() {
    return payloadEnabled;
  }

  public void setPayloadEnabled(boolean v) {
    payloadEnabled = v;
  }

  public boolean isWebEnabled() {
    return webEnabled;
  }

  public void setWebEnabled(boolean v) {
    webEnabled = v;
  }

  public boolean isOperationEnabled() {
    return operationEnabled;
  }

  public void setOperationEnabled(boolean v) {
    operationEnabled = v;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String v) {
    application = v;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String v) {
    environment = v;
  }

  public String getApplicationPackage() {
    return applicationPackage;
  }

  public void setApplicationPackage(String v) {
    applicationPackage = v;
  }

  public String getTraceIdHeader() {
    return traceIdHeader;
  }

  public void setTraceIdHeader(String v) {
    traceIdHeader = v;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int v) {
    maxDepth = v;
  }

  public int getMaxItems() {
    return maxItems;
  }

  public void setMaxItems(int v) {
    maxItems = v;
  }

  public int getMaxString() {
    return maxString;
  }

  public void setMaxString(int v) {
    maxString = v;
  }

  public int getMaxPayloadBytes() {
    return maxPayloadBytes;
  }

  public void setMaxPayloadBytes(int v) {
    maxPayloadBytes = v;
  }

  public int getMaxFrames() {
    return maxFrames;
  }

  public void setMaxFrames(int v) {
    maxFrames = v;
  }

  public List<String> getPayloadPaths() {
    return payloadPaths;
  }

  public void setPayloadPaths(List<String> v) {
    payloadPaths = List.copyOf(v);
  }

  public List<String> getPropagationHosts() {
    return propagationHosts;
  }

  public void setPropagationHosts(List<String> hosts) {
    propagationHosts = List.copyOf(hosts);
  }

  public void validate() {
    if (traceIdHeader == null || !traceIdHeader.matches("[A-Za-z0-9-]{1,64}"))
      throw new IllegalArgumentException("Invalid trace header");
    if (maxDepth < 1
        || maxDepth > 32
        || maxItems < 1
        || maxItems > 1000
        || maxString < 1
        || maxString > 65536
        || maxPayloadBytes < 64
        || maxPayloadBytes > 1048576) throw new IllegalArgumentException("Invalid logging limits");
    if (application == null
        || environment == null
        || application.length() > 128
        || environment.length() > 128)
      throw new IllegalArgumentException("Invalid logging application metadata");
  }
}
