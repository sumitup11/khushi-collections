package com.khushi;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class App {
  public static void main(String[] a) { SpringApplication.run(App.class, a); }

  @Bean
  CommandLineRunner seed(ProductRepo repo) {
    return args -> {
      if (repo.count() > 0) return;
      Object[][] d = {
        {"Banarasi Silk Saree", "Saree", 2499, 3499}, {"Cotton Printed Kurti", "Kurti", 799, 1199},
        {"Bridal Red Lehenga", "Lehenga", 7999, 11999}, {"Kundan Necklace Set", "Jewellery", 1299, 1999},
        {"Designer Handbag", "Bags", 1599, 2299}, {"Gold Jhumka Earrings", "Jewellery", 399, 699},
        {"Chikankari Kurti", "Kurti", 1199, 1699}, {"Georgette Party Saree", "Saree", 1799, 2599},
        {"Embroidered Clutch", "Bags", 699, 999}, {"Wedding Sharara Set", "Lehenga", 4999, 6999}
      };
      for (Object[] x : d) {
        Product p = new Product();
        p.name = (String) x[0]; p.category = (String) x[1]; p.price = (Integer) x[2]; p.oldPrice = (Integer) x[3];
        repo.save(p);
      }
    };
  }
}
