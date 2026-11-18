package com.khushi;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "orders")
public class Orders {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String email, name, phone, pay, status, rzpOrderId, rzpPaymentId;
  @Column(length = 500) public String addr;
  @Column(length = 4000) public String itemsJson;
  public int total;
  public Instant createdAt;
}
