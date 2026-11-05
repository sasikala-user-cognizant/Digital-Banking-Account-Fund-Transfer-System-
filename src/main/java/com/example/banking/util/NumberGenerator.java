package com.example.banking.util;

import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates business identifiers. Uniqueness is still enforced by DB unique constraints. */
@Component
public class NumberGenerator {

    private final SecureRandom random = new SecureRandom();

    /** 12-digit account number, first digit never zero. */
    public String newAccountNumber() {
        StringBuilder sb = new StringBuilder(12);
        sb.append(1 + random.nextInt(9));
        for (int i = 1; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** e.g. CUST4829173650 (14 chars). */
    public String newCustomerNumber() {
        return "CUST" + String.format("%010d", Math.floorMod(random.nextLong(), 10_000_000_000L));
    }

    public String newReference() {
        return UUID.randomUUID().toString();
    }
}
