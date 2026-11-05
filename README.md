# Digital Banking Account & Fund Transfer System

A backend banking application built with **Java 17, Spring Boot 3, Spring Data JPA**.
It manages customers and accounts, deposits, withdrawals, fund transfers and transaction history,
with business validations, standardised error responses and Swagger/OpenAPI documentation.

> All customer, account and transaction data is **synthetic**.

## Project status

| Step | Scope | Status |
|---|---|---|
| 1 | Project setup, entities, repositories, exception-handling skeleton | Done |
| 2 | Customer and account services, controllers, DTOs, tests | Done |
| 3 | Deposit, withdrawal, ledger entries, idempotency, synthetic data seeder | Done |
| 4 | Fund transfer (atomic, deadlock-safe, idempotent) | Done |
| 5 | Transaction history (paging, filters) and lookup by reference | Next |
| 6-9 | Swagger examples, concurrency tests, coverage report, demo and presentation | Planned |

Endpoints appear in Swagger UI as each step is delivered.

### Endpoints available now (Steps 2 to 4)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/customers` | Create customer |
| GET | `/api/v1/customers/{customerId}` | Get customer |
| GET | `/api/v1/customers?page=0&size=20` | List customers (paged, max size 100) |
| PUT | `/api/v1/customers/{customerId}` | Update name, email, phone |
| PATCH | `/api/v1/customers/{customerId}/status` | ACTIVE / INACTIVE |
| POST | `/api/v1/customers/{customerId}/accounts` | Open account (SAVINGS / CURRENT) |
| GET | `/api/v1/customers/{customerId}/accounts` | List a customer's accounts |
| GET | `/api/v1/accounts/{accountNumber}` | Account details |
| GET | `/api/v1/accounts/{accountNumber}/balance` | Balance enquiry |
| PATCH | `/api/v1/accounts/{accountNumber}/status` | ACTIVE / INACTIVE / FROZEN / CLOSED |
| POST | `/api/v1/accounts/{accountNumber}/deposit` | Deposit (optional `Idempotency-Key` header) |
| POST | `/api/v1/accounts/{accountNumber}/withdraw` | Withdraw (optional `Idempotency-Key` header) |
| POST | `/api/v1/transfers` | Fund transfer between two accounts (optional `Idempotency-Key` header) |

Quick try (PowerShell, app running on 8080):
```powershell
curl.exe -X POST http://localhost:8080/api/v1/customers -H "Content-Type: application/json" -d '{\"firstName\":\"Asha\",\"lastName\":\"Raman\",\"email\":\"asha@example.com\",\"phone\":\"9876543210\",\"dateOfBirth\":\"1990-05-15\"}'
curl.exe -X POST http://localhost:8080/api/v1/customers/1/accounts -H "Content-Type: application/json" -d '{\"accountType\":\"SAVINGS\"}'
```
(Or just use Swagger UI, which is easier.)

### Demo data (synthetic)

On startup the app loads 5 synthetic customers through the real services, so every opening balance has a
ledger entry. Watch the console for lines like `customer id=1 Asha Raman | account 4837291056xx | SAVINGS 25000.00`
to get the account numbers (they are random each run). Customer 4 has a low balance (1000.00) for the
insufficient-balance demo, and customer 5's account is FROZEN for the inactive-account demo.
Turn seeding off with `banking.seed.enabled: false`.

Deposit example:
```json
POST /api/v1/accounts/{accountNumber}/deposit
{ "amount": 5000.00, "description": "Salary credit" }
```
```json
201 Created
{ "transactionRef": "b7c1f6e2-...", "accountNumber": "...", "type": "DEPOSIT", "amount": 5000.00,
  "balanceAfter": 30000.00, "status": "SUCCESS", "description": "Salary credit", "createdAt": "..." }
```

### Fund transfer

```json
POST /api/v1/transfers
Idempotency-Key: rent-oct-2026        (optional)
{ "fromAccount": "100000000001", "toAccount": "100000000002", "amount": 2500.00, "description": "Rent" }
```
```json
201 Created
{ "transferRef": "5d0a9a53-...", "fromAccount": "100000000001", "toAccount": "100000000002",
  "amount": 2500.00, "status": "SUCCESS", "fromBalanceAfter": 22500.00, "description": "Rent", "createdAt": "..." }
```
Failure example (insufficient balance):
```json
422 Unprocessable Entity
{ "timestamp": "...", "status": 422, "error": "INSUFFICIENT_BALANCE",
  "message": "Available balance 1000.00 in account 100000000001 is less than requested amount 2500.00",
  "path": "/api/v1/transfers" }
```

How it stays consistent: the whole transfer runs in ONE database transaction. Both account rows are locked
(`SELECT ... FOR UPDATE`) in ascending account-number order, so A->B and B->A at the same time cannot deadlock.
The debit, the credit and both ledger rows (sharing one `transferRef`) are committed together or rolled back together.
Only the sender's balance is returned in the response.

Transfer rules: both accounts must exist and be ACTIVE; they must differ; currencies must match; the sender needs
enough balance and must stay within the daily debit limit; the amount rules below apply.

### Money rules enforced

- Amount must be greater than 0 and have at most 2 decimal places (`INVALID_AMOUNT`, 400).
- Amount cannot exceed `banking.limits.max-per-transaction` (`LIMIT_EXCEEDED`, 422).
- Only ACTIVE accounts can transact (`ACCOUNT_INACTIVE`, 422).
- Withdrawal needs enough balance (`INSUFFICIENT_BALANCE`, 422) and must stay within the daily debit limit.
- `Idempotency-Key` (max 64 chars): repeating the same request returns the original result without
  moving money again; reusing a key for a different request returns 409.

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer (21 works) | `java -version` |
| Apache Maven | 3.9 or newer | `mvn -version` |
| Git (optional) | any | `git --version` |
| PostgreSQL (optional) | 14+ | only for the `postgres` profile |

Windows: set `JAVA_HOME` to the JDK folder and add Maven's `bin` folder to `PATH`,
then open a **new** terminal.

## 2. Get the code

```bash
unzip digital-banking-step1.zip
cd digital-banking        # the folder that contains pom.xml
```

## 3. Build and test

```bash
mvn clean test
```

Expected: `BUILD SUCCESS`. The JaCoCo coverage report is written to `target/site/jacoco/index.html`.

## 4. Run the application

**Default (in-memory H2, no setup needed):**
```bash
mvn spring-boot:run
```
The console shows `Started DigitalBankingApplication` and the app listens on **http://localhost:8080**.
H2 data is lost on every restart.

**Change the port:**
```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

**Build and run a jar:**
```bash
mvn clean package
java -jar target/digital-banking-0.0.1-SNAPSHOT.jar
```

## 5. Useful URLs

| What | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| H2 console | http://localhost:8080/h2-console |

H2 console login: JDBC URL `jdbc:h2:mem:bankdb`, user `sa`, empty password.
You should see the tables `CUSTOMER`, `ACCOUNT` and `TRANSACTIONS`.

## 6. Using PostgreSQL (optional)

1. Create a database:
   ```sql
   CREATE DATABASE bankdb;
   CREATE USER bank WITH PASSWORD 'bank';
   GRANT ALL PRIVILEGES ON DATABASE bankdb TO bank;
   ```
2. Run with the profile (PowerShell shown; use `export` on Linux/macOS):
   ```powershell
   $env:DB_USER="bank"; $env:DB_PASSWORD="bank"
   mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
   ```

## 7. Configuration

Edit `src/main/resources/application.yml`:

| Key | Default | Meaning |
|---|---|---|
| `banking.limits.max-per-transaction` | 200000.00 | Maximum amount for a single transaction |
| `banking.limits.daily-debit-limit` | 500000.00 | Maximum total debits per account per day |
| `banking.account.default-currency` | INR | Currency for new accounts |
| `banking.account.minimum-customer-age` | 18 | Minimum age to register |

## 8. VS Code setup

1. Install the **Extension Pack for Java**, **Lombok Annotations Support for VS Code**
   and (optionally) **Spring Boot Extension Pack**.
2. **File > Open Folder** and select the folder that contains `pom.xml`.
3. Wait for the Java import to finish (spinner at bottom right).
4. Run `mvn clean test`, then `mvn spring-boot:run` in the integrated terminal.

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| `mvn` is not recognised | Install Maven, add its `bin` to `PATH`, open a new terminal |
| `release version 17 not supported` | Your JDK is older than 17. Install JDK 17+ and set `JAVA_HOME` |
| Red errors on getters or `builder()` in VS Code | Install the Lombok extension, then run **Java: Clean Java Language Server Workspace** |
| Port 8080 already in use | Use the port-change command in section 4 |
| "Failed to fetch generations from Spring IO" | Harmless extension warning. Ignore it, Maven does not use it |
| Dependency download fails | Check internet, VPN or proxy; configure the proxy in `~/.m2/settings.xml` |

## 10. Project structure

```
src/main/java/com/example/banking
  config/       OpenAPI, JPA auditing, typed properties
  entity/       Customer, Account, Transaction and enums
  repository/   Spring Data JPA repositories (incl. locking queries)
  exception/    Error codes, business exceptions, GlobalExceptionHandler
  dto/          Request and response records with validation rules
  mapper/       Entity to DTO mapping
  service/      CustomerService, AccountService (business rules)
  controller/   CustomerController, AccountController
  util/         Account / customer / reference number generation
src/main/resources/application.yml, application-postgres.yml
src/test/java/   Unit and integration tests
```
