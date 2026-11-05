package com.example.banking.repository;

import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionRef(String transactionRef);

    Optional<Transaction> findByAccountIdAndIdempotencyKey(Long accountId, String idempotencyKey);

    List<Transaction> findByTransferRef(String transferRef);

    /** History with optional type and date-range filters (null means "no filter"). */
    @Query("""
            select t from Transaction t
            where t.account.accountNumber = :accountNumber
              and (:type is null or t.type = :type)
              and (:fromTs is null or t.createdAt >= :fromTs)
              and (:toTs is null or t.createdAt <= :toTs)
            """)
    Page<Transaction> search(@Param("accountNumber") String accountNumber,
                             @Param("type") TransactionType type,
                             @Param("fromTs") Instant from,
                             @Param("toTs") Instant to,
                             Pageable pageable);

    /** Sum of successful debits since a point in time, used for the daily limit rule. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.id = :accountId
              and t.status = com.example.banking.entity.TransactionStatus.SUCCESS
              and t.type in (com.example.banking.entity.TransactionType.WITHDRAWAL,
                             com.example.banking.entity.TransactionType.TRANSFER_DEBIT)
              and t.createdAt >= :since
            """)
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("since") Instant since);
}
