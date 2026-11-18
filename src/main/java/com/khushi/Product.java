package com.khushi;

import jakarta.persistence.*;

@Entity
public class Product {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String name, category;
  public int price, oldPrice;
}
