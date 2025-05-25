package com.yourcompany.etsystoktakip.repository;

import com.yourcompany.etsystoktakip.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Ek sorgular burada tanımlanabilir
}
