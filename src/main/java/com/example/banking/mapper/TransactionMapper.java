package com.example.banking.mapper;

import com.example.banking.dto.response.TransactionResponse;
import com.example.banking.entity.Transaction;
import org.springframework.stereotype.Component;

/** Call from inside a transaction: it touches the lazy account association. */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getTransactionRef(), t.getAccount().getAccountNumber(), t.getType(),
                t.getAmount(), t.getBalanceAfter(), t.getStatus(), t.getCounterpartyAccount(),
                t.getTransferRef(), t.getDescription(), t.getCreatedAt());
    }
}
