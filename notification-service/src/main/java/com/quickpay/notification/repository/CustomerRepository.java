package com.quickpay.notification.repository;

import com.quickpay.notification.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer,String> {

    Optional<Customer> findCustomerByCif(String cif);
}
