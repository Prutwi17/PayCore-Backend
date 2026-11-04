package com.paycore.payment.repository;

import com.paycore.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(Long paymentId);

    Optional<PaymentAttempt> findByRequestReference(String requestReference);
}
