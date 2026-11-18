package com.khushi;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdersRepo extends JpaRepository<Orders, Long> { List<Orders> findByEmailOrderByIdDesc(String email); }
