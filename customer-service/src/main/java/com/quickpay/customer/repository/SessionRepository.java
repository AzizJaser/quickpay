package com.quickpay.customer.repository;

import com.quickpay.customer.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session,String> {

    Optional<Session> findByTokenHash(String tokenHash);

    void deleteByCif(String cif);
}
