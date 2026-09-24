package com.quickpay.customer.repository;

import com.quickpay.customer.domain.Customer;
import com.quickpay.customer.enums.CustomerStatus;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.quickpay.customer.enums.CustomerStatus.CLOSED;

@Repository
public interface CustomerRepository extends JpaRepository<Customer,String> {

    boolean existsByNationalIdAndStatusNot(String nationalId, CustomerStatus status);

    @Query(value = "select nextval('seq_customers_cif')",nativeQuery = true)
    Long getNextSeq();

    Optional<Customer> findByEmailAndStatus(String email, CustomerStatus status);

    Optional<Customer> findByCif(String cif);
}
