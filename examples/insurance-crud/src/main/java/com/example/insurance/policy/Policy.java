package com.example.insurance.policy;

import java.math.BigDecimal;
import javax.persistence.*;

@Entity
@Table(
    name = "insurance_policies",
    uniqueConstraints = @UniqueConstraint(name = "uk_policy_number", columnNames = "policy_number"))
public class Policy {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "policy_number", nullable = false, length = 40)
  private String policyNumber;

  @Column(nullable = false, length = 120)
  private String holderName;

  @Column(nullable = false, length = 254)
  private String holderEmail;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal premium;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PolicyStatus status;

  protected Policy() {}

  public Policy(PolicyRequest request) {
    update(request);
  }

  public void update(PolicyRequest request) {
    this.policyNumber = request.policyNumber();
    this.holderName = request.holderName();
    this.holderEmail = request.holderEmail();
    this.premium = request.premium();
    this.status = request.status();
  }

  public Long getId() {
    return id;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public String getHolderName() {
    return holderName;
  }

  public String getHolderEmail() {
    return holderEmail;
  }

  public BigDecimal getPremium() {
    return premium;
  }

  public PolicyStatus getStatus() {
    return status;
  }
}
