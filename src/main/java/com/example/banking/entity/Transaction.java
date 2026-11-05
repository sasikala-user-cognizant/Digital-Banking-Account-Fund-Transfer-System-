package com.example.banking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Immutable ledger entry. Rows are only ever inserted, never updated or deleted. */
@Entity
@Table(name = "transactions",
        indexes = {
                @Index(name = "idx_txn_account_created", columnList = "account_id, created_at"),
                @Index(name = "idx_txn_transfer_ref", columnList = "transfer_ref")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_txn_account_idempotency", columnNames = {"account_id", "idempotency_key"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 36, updatable = false)
    private String transactionRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @Column(name = "counterparty_account", length = 12, updatable = false)
    private String counterpartyAccount;

    @Column(name = "transfer_ref", length = 36, updatable = false)
    private String transferRef;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(length = 200, updatable = false)
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
