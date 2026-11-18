# Financial Calculation Logic & Rules

## 1. Account Balances
- **Formula**: `Opening Balance + Posted Inflows - Posted Outflows + Adjustments`
- Liquid accounts comprise:
  - `CASH`: Hand cash, personal cash, home cash
  - `BANK`: Checking/savings bank accounts (e.g., DBBL, City Bank, BRAC)
  - `MOBILE_BANKING`: Wallets (bKash, Nagad, Rocket, Upay)

## 2. Income & Expense Calculations
- **Total Income**: Genuine Income transactions only. Excludes internal transfers, loan disbursements received, savings withdrawals, and repayments.
- **Total Expense**: Genuine Expenses minus linked Refunds. Excludes internal transfers, loan given, savings deposits, and principal repayments.
- **Transfers**: Inter-account movements update source (outflow) and destination (inflow) balances without affecting Income or Expense totals.
- **Refunds**: Deducted directly from total expense rather than treated as new income.

## 3. Loans & Receivables / Payables
- **Loan Given**:
  - Treated as a financial receivable asset, not an expense.
  - Funds deducted from source account.
  - Outstanding = `Principal - Repayments Received`.
- **Loan Taken**:
  - Treated as a financial liability, not income.
  - Funds deposited into target account.
  - Outstanding = `Principal - Repayments Paid`.

## 4. Net Worth
- **Formula**: `Available Liquid Balances + Savings & DPS/FDR + Outstanding Loan Given (Receivable) - Outstanding Loan Taken (Payable)`
- Counted once without double-counting.
