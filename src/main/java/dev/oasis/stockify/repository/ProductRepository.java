package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Ek sorgular burada tanımlanabilir
}
