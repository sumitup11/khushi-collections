package com.khushi;

import jakarta.persistence.*;

@Entity @Table(name = "users")
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(unique = true, nullable = false) public String email;
  public String name, passwordHash;
}
