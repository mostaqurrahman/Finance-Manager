import {
  Account,
  Asset,
  Category,
  Liability,
  Loan,
  LoanRepayment,
  Person,
  SavingsGoal,
  SavingsProduct,
  Transaction,
  TransactionType,
} from '../types/finance';
import {
  calculateAccountBalances,
  calculateLoanGivenOutstanding,
  calculateLoanTakenOutstanding,
  calculateTotalAssets,
  calculateTotalLiabilities,
  calculateTotalSavings,
  filterTransactionsByDate,
} from './ledgerEngine';
import { formatDhakaDateTime } from '../i18n/formatters';

export type ReportPeriodType = 'monthly' | 'yearly';
export type ReportExportFormat = 'pdf' | 'xlsx' | 'both';

export interface AvailableReportPeriod {
  key: string; // YYYY-MM or YYYY
  labelEn: string; // e.g. "November 2026" or "2026"
  labelBn: string; // e.g. "নভেম্বর ২০২৬" or "২০২৬"
  startDate: string; // YYYY-MM-DD
  endDate: string; // YYYY-MM-DD
  txCount: number;
}

export interface TransactionTypeSummaryRow {
  type: TransactionType;
  label: string;
  count: number;
  totalAmount: number;
}

export interface PeriodAccountSummaryRow {
  accountId: string;
  accountName: string;
  accountType: string;
  provider: string;
  accountNumber: string;
  periodOpeningBalance: number;
  moneyIn: number;
  moneyOut: number;
  netChange: number;
  periodClosingBalance: number;
  liveCurrentBalance: number;
  txCount: number;
  status: string;
}

export interface AccountStatementEntry {
  transactionId: string;
  date: string;
  occurredAt: string;
  type: string;
  counterpartyOrDetail: string;
  category: string;
  person: string;
  description: string;
  moneyIn: number;
  moneyOut: number;
  runningBalance: number;
}

export interface AccountStatementReport {
  accountId: string;
  accountName: string;
  accountType: string;
  provider: string;
  openingBalance: number;
  closingBalance: number;
  totalMoneyIn: number;
  totalMoneyOut: number;
  entries: AccountStatementEntry[];
}

export interface PeriodTrendBucket {
  bucketKey: string;
  label: string;
  txCount: number;
  income: number;
  grossExpense: number;
  refunds: number;
  netExpense: number;
  netCashFlow: number;
}

export interface PersonSummaryRow {
  personId: string;
  personName: string;
  personNameBn: string;
  relationship: string;
  txCount: number;
  totalExpense: number;
  totalIncome: number;
  totalOtherFlow: number;
  expenseSharePercent: number;
}

export interface PersonDetailedTransactionRow {
  personId: string;
  personName: string;
  relationship: string;
  date: string;
  type: string;
  accountName: string;
  categoryName: string;
  amount: number;
  description: string;
}

export interface CategorySummaryRow {
  categoryId: string;
  categoryName: string;
  categoryNameBn: string;
  type: 'income' | 'expense';
  txCount: number;
  amount: number;
  sharePercent: number;
}

export interface CategoryDetailedTransactionRow {
  categoryId: string;
  categoryName: string;
  categoryType: 'income' | 'expense';
  date: string;
  accountName: string;
  personName: string;
  amount: number;
  description: string;
}

export interface UnifiedLoanSummary {
  totalGivenPrincipal: number;
  totalGivenPaid: number;
  totalGivenOutstanding: number;
  totalTakenPrincipal: number;
  totalTakenPaid: number;
  totalTakenOutstanding: number;
  loans: Array<{
    id: string;
    type: 'given' | 'taken';
    personName: string;
    originalAmount: number;
    paidAmount: number;
    remainingAmount: number;
    startDate: string;
    dueDate: string;
    status: string;
    note: string;
  }>;
  periodRepayments: Array<{
    id: string;
    loanId: string;
    loanType: 'given' | 'taken';
    partyName: string;
    accountName: string;
    amount: number;
    date: string;
    note: string;
  }>;
}

export interface UnifiedFinancialReport {
  metadata: {
    reportTitle: string;
    periodType: ReportPeriodType;
    periodKey: string;
    periodLabelEn: string;
    periodLabelBn: string;
    startDate: string;
    endDate: string;
    generatedAtDhaka: string;
    ownerName: string;
    ownerEmail: string;
    transactionCount: number;
  };
  overallCashFlow: {
    totalIncome: number;
    grossExpense: number;
    totalRefunds: number;
    transferFeeExpense: number;
    netExpense: number;
    netCashFlow: number;
    savingsRatePercent: number;
    transferVolume: number;
    loanGivenInPeriod: number;
    loanReceivedInPeriod: number;
    loanRepaymentReceivedInPeriod: number;
    loanRepaymentPaidInPeriod: number;
    savingsDepositInPeriod: number;
    savingsWithdrawalInPeriod: number;
  };
  netPosition: {
    availableBalance: number;
    cashBalance: number;
    bankBalance: number;
    mobileBankingBalance: number;
    totalSavings: number;
    loanGivenOutstanding: number;
    loanTakenOutstanding: number;
    totalAssets: number;
    totalLiabilities: number;
    netWorth: number;
  };
  transactionTypeSummary: TransactionTypeSummaryRow[];
  accountSummaries: PeriodAccountSummaryRow[];
  accountStatements: AccountStatementReport[];
  periodTrendAnalysis: PeriodTrendBucket[];
  personSummaries: PersonSummaryRow[];
  personDetailedTransactions: PersonDetailedTransactionRow[];
  categorySummaries: CategorySummaryRow[];
  categoryDetailedTransactions: CategoryDetailedTransactionRow[];
  loanSummary: UnifiedLoanSummary;
  savingsSummary: {
    totalSavingsBalance: number;
    periodDeposits: number;
    periodWithdrawals: number;
    savingsAccounts: PeriodAccountSummaryRow[];
    savingsProducts: SavingsProduct[];
    savingsGoals: SavingsGoal[];
  };
  assetsAndLiabilities: {
    assets: Asset[];
    liabilities: Liability[];
    totalPhysicalAssets: number;
    totalOtherLiabilities: number;
  };
  transactions: Array<{
    id: string;
    date: string;
    occurredAt: string;
    type: string;
    amount: number;
    netExpense: number | null;
    accountName: string;
    destinationAccountName: string;
    categoryName: string;
    personName: string;
    paymentMethod: string;
    description: string;
    note: string;
    status: string;
  }>;
}

const MONTH_NAMES_EN = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

const MONTH_NAMES_BN = [
  'জানুয়ারি',
  'ফেব্রুয়ারি',
  'মার্চ',
  'এপ্রিল',
  'মে',
  'জুন',
  'জুলাই',
  'আগস্ট',
  'সেপ্টেম্বর',
  'অক্টোবর',
  'নভেম্বর',
  'ডিসেম্বর',
];

function toBengaliDigits(input: string): string {
  const map = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
  return input.replace(/\d/g, (d) => map[Number(d)]);
}

function getLastDayOfMonth(year: number, month1To12: number): string {
  const d = new Date(year, month1To12, 0);
  const mm = String(month1To12).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${year}-${mm}-${dd}`;
}

export function getPeriodBounds(
  periodType: ReportPeriodType,
  periodKey: string
): { startDate: string; endDate: string; labelEn: string; labelBn: string } {
  if (periodType === 'monthly') {
    const [yStr, mStr] = periodKey.split('-');
    const year = parseInt(yStr, 10) || new Date().getFullYear();
    const month = Math.min(12, Math.max(1, parseInt(mStr, 10) || 1));
    const mm = String(month).padStart(2, '0');
    return {
      startDate: `${year}-${mm}-01`,
      endDate: getLastDayOfMonth(year, month),
      labelEn: `${MONTH_NAMES_EN[month - 1]} ${year}`,
      labelBn: `${MONTH_NAMES_BN[month - 1]} ${toBengaliDigits(String(year))}`,
    };
  }
  const year = parseInt(periodKey, 10) || new Date().getFullYear();
  return {
    startDate: `${year}-01-01`,
    endDate: `${year}-12-31`,
    labelEn: `${year}`,
    labelBn: toBengaliDigits(String(year)),
  };
}

/**
 * Dynamically extracts available non-empty months or years from user's active transactions
 * (newest to oldest). No hardcoded months or empty periods.
 */
export function getAvailableReportPeriods(
  transactions: Transaction[],
  periodType: ReportPeriodType
): AvailableReportPeriod[] {
  const counts = new Map<string, number>();

  for (const tx of transactions) {
    if (tx.deletedAt || tx.status === 'cancelled') continue;
    const datePart = (tx.occurredAt || '').substring(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(datePart)) continue;
    const key = periodType === 'monthly' ? datePart.substring(0, 7) : datePart.substring(0, 4);
    counts.set(key, (counts.get(key) || 0) + 1);
  }

  return Array.from(counts.entries())
    .sort((a, b) => b[0].localeCompare(a[0])) // Newest to oldest
    .map(([key, txCount]) => {
      const bounds = getPeriodBounds(periodType, key);
      return {
        key,
        labelEn: bounds.labelEn,
        labelBn: bounds.labelBn,
        startDate: bounds.startDate,
        endDate: bounds.endDate,
        txCount,
      };
    });
}

export function getUnifiedReportFileName(
  periodType: ReportPeriodType,
  periodKey: string,
  ext: 'pdf' | 'xlsx'
): string {
  const safeKey = periodKey.replace(/[^a-zA-Z0-9_-]/g, '');
  const prefix = periodType === 'monthly' ? 'HisabKhata_Monthly' : 'HisabKhata_Yearly';
  return `${prefix}_${safeKey}.${ext}`;
}

/**
 * Helper: Computes Money In & Money Out impact of a single transaction on a specific account.
 */
function getTransactionAccountImpact(
  tx: Transaction,
  accountId: string
): { moneyIn: number; moneyOut: number } {
  let moneyIn = 0;
  let moneyOut = 0;

  if (tx.accountId === accountId) {
    switch (tx.type) {
      case 'income':
      case 'loan_received':
      case 'loan_repayment_received':
      case 'savings_withdrawal':
      case 'asset_sale':
      case 'refund':
        moneyIn += tx.amount;
        break;
      case 'expense':
      case 'loan_given':
      case 'loan_repayment_paid':
      case 'savings_deposit':
      case 'asset_purchase':
        moneyOut += tx.amount;
        break;
      case 'transfer':
        moneyOut += tx.amount + (tx.transferFee || 0);
        break;
      case 'balance_adjustment':
        if (tx.amount >= 0) moneyIn += tx.amount;
        else moneyOut += Math.abs(tx.amount);
        break;
    }
  }

  if (tx.destinationAccountId === accountId) {
    if (tx.type === 'transfer' || tx.type === 'savings_deposit') {
      moneyIn += tx.amount;
    } else if (tx.type === 'savings_withdrawal') {
      moneyOut += tx.amount;
    }
  }

  return { moneyIn, moneyOut };
}

export interface BuildUnifiedReportParams {
  periodType: ReportPeriodType;
  periodKey: string;
  ownerName?: string | null;
  ownerEmail?: string | null;
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  people: Person[];
  loans: Loan[];
  loanRepayments?: LoanRepayment[];
  savingsProducts?: SavingsProduct[];
  savingsGoals?: SavingsGoal[];
  assets?: Asset[];
  liabilities?: Liability[];
}

export function buildUnifiedFinancialReport(
  params: BuildUnifiedReportParams
): UnifiedFinancialReport {
  const {
    periodType,
    periodKey,
    ownerName,
    ownerEmail,
    accounts,
    transactions,
    categories,
    people,
    loans,
    loanRepayments = [],
    savingsProducts = [],
    savingsGoals = [],
    assets = [],
    liabilities = [],
  } = params;

  const bounds = getPeriodBounds(periodType, periodKey);
  const { startDate, endDate, labelEn, labelBn } = bounds;

  const accountMap = new Map(accounts.map((a) => [a.id, a]));
  const categoryMap = new Map(categories.map((c) => [c.id, c]));
  const personMap = new Map(people.map((p) => [p.id, p]));

  const activeAllTx = transactions
    .filter((t) => !t.deletedAt && t.status !== 'cancelled')
    .slice()
    .sort((a, b) => (a.occurredAt || '').localeCompare(b.occurredAt || ''));

  const beforePeriodTx = activeAllTx.filter(
    (t) => (t.occurredAt || '').substring(0, 10) < startDate
  );
  const periodTxAsc = filterTransactionsByDate(activeAllTx, { startDate, endDate }).sort((a, b) =>
    (a.occurredAt || '').localeCompare(b.occurredAt || '')
  );
  const upToEndDateTx = activeAllTx.filter(
    (t) => (t.occurredAt || '').substring(0, 10) <= endDate
  );

  // 1. Live & As-Of-End-Date Balances + Net Worth Snapshot
  const liveBalances = calculateAccountBalances(accounts, activeAllTx);
  const endDateBalances = calculateAccountBalances(accounts, upToEndDateTx);

  let cashBal = 0;
  let bankBal = 0;
  let mobileBal = 0;
  for (const acc of accounts) {
    if (acc.status !== 'active') continue;
    const bal = endDateBalances[acc.id] ?? 0;
    if (acc.type === 'cash') cashBal += bal;
    else if (acc.type === 'bank') bankBal += bal;
    else if (acc.type === 'mobile_banking') mobileBal += bal;
  }
  const availableBalance = cashBal + bankBal + mobileBal;

  const periodAndPriorLoans = loans.filter(
    (l) => !l.deletedAt && (!l.startDate || l.startDate <= endDate)
  );
  const upToEndRepayments = loanRepayments.filter((r) => !r.date || r.date <= endDate);
  const loanGivenOutstanding = calculateLoanGivenOutstanding(periodAndPriorLoans, upToEndRepayments);
  const loanTakenOutstanding = calculateLoanTakenOutstanding(periodAndPriorLoans, upToEndRepayments);

  const totalSavings = calculateTotalSavings(accounts, endDateBalances, savingsProducts);
  const totalAssets = calculateTotalAssets(
    accounts,
    endDateBalances,
    savingsProducts,
    assets,
    loanGivenOutstanding
  );
  const totalLiabilities = calculateTotalLiabilities(loanTakenOutstanding, liabilities);
  const netWorth = totalAssets - totalLiabilities;

  // 2. Overall Income, Expense, Refund, Cash Flow & Transaction Type Summary
  const refundsByExpense: Record<string, number> = {};
  for (const tx of periodTxAsc) {
    if (tx.type === 'refund' && tx.originalExpenseId) {
      refundsByExpense[tx.originalExpenseId] =
        (refundsByExpense[tx.originalExpenseId] || 0) + tx.amount;
    }
  }

  let totalIncome = 0;
  let grossExpense = 0;
  let totalRefunds = 0;
  let transferFeeExpense = 0;
  let netExpense = 0;
  let transferVolume = 0;
  let loanGivenInPeriod = 0;
  let loanReceivedInPeriod = 0;
  let loanRepaymentReceivedInPeriod = 0;
  let loanRepaymentPaidInPeriod = 0;
  let savingsDepositInPeriod = 0;
  let savingsWithdrawalInPeriod = 0;

  const typeAgg = new Map<TransactionType, { count: number; totalAmount: number }>();

  for (const tx of periodTxAsc) {
    const prevAgg = typeAgg.get(tx.type) || { count: 0, totalAmount: 0 };
    prevAgg.count += 1;
    prevAgg.totalAmount += tx.amount;
    typeAgg.set(tx.type, prevAgg);

    switch (tx.type) {
      case 'income':
        totalIncome += tx.amount;
        break;
      case 'expense': {
        grossExpense += tx.amount;
        const refunded = refundsByExpense[tx.id] || tx.refundedAmount || 0;
        netExpense += Math.max(0, tx.amount - refunded);
        break;
      }
      case 'refund':
        totalRefunds += tx.amount;
        break;
      case 'transfer':
        transferVolume += tx.amount;
        if (tx.transferFee) {
          transferFeeExpense += tx.transferFee;
          netExpense += tx.transferFee;
        }
        break;
      case 'loan_given':
        loanGivenInPeriod += tx.amount;
        break;
      case 'loan_received':
        loanReceivedInPeriod += tx.amount;
        break;
      case 'loan_repayment_received':
        loanRepaymentReceivedInPeriod += tx.amount;
        break;
      case 'loan_repayment_paid':
        loanRepaymentPaidInPeriod += tx.amount;
        break;
      case 'savings_deposit':
        savingsDepositInPeriod += tx.amount;
        break;
      case 'savings_withdrawal':
        savingsWithdrawalInPeriod += tx.amount;
        break;
    }
  }

  const netCashFlow = totalIncome - netExpense;
  const savingsRatePercent =
    totalIncome > 0 ? Math.round((netCashFlow / totalIncome) * 1000) / 10 : 0;

  const transactionTypeSummary: TransactionTypeSummaryRow[] = Array.from(typeAgg.entries())
    .map(([type, data]) => ({
      type,
      label: type.replace(/_/g, ' ').toUpperCase(),
      count: data.count,
      totalAmount: Math.round(data.totalAmount),
    }))
    .sort((a, b) => b.totalAmount - a.totalAmount);

  // 3. Account-wise Summary & Individual Account Statements (with pre-period Opening Balances)
  const accountSummaries: PeriodAccountSummaryRow[] = [];
  const accountStatements: AccountStatementReport[] = [];

  for (const acc of accounts) {
    let opening = acc.openingBalance || 0;
    for (const bTx of beforePeriodTx) {
      const { moneyIn, moneyOut } = getTransactionAccountImpact(bTx, acc.id);
      opening += moneyIn - moneyOut;
    }

    let running = opening;
    let totalMoneyIn = 0;
    let totalMoneyOut = 0;
    const entries: AccountStatementEntry[] = [];

    for (const tx of periodTxAsc) {
      const { moneyIn, moneyOut } = getTransactionAccountImpact(tx, acc.id);
      if (moneyIn === 0 && moneyOut === 0) continue;

      totalMoneyIn += moneyIn;
      totalMoneyOut += moneyOut;
      running += moneyIn - moneyOut;

      const cat = tx.categoryId ? categoryMap.get(tx.categoryId) : undefined;
      const person = tx.personId ? personMap.get(tx.personId) : undefined;

      let counterparty = tx.paymentMethod || '';
      if (tx.type === 'transfer') {
        if (tx.accountId === acc.id) {
          const dest = tx.destinationAccountId
            ? accountMap.get(tx.destinationAccountId)?.name || tx.destinationAccountId
            : 'External';
          counterparty = `To ${dest}`;
        } else {
          const src = accountMap.get(tx.accountId)?.name || tx.accountId;
          counterparty = `From ${src}`;
        }
      } else if (person) {
        counterparty = person.relationship ? `${person.name} (${person.relationship})` : person.name;
      }

      entries.push({
        transactionId: tx.id,
        date: (tx.occurredAt || '').substring(0, 10),
        occurredAt: tx.occurredAt || '',
        type: tx.type.replace(/_/g, ' ').toUpperCase(),
        counterpartyOrDetail: counterparty || '-',
        category: cat ? cat.name : tx.categoryId || '-',
        person: person ? person.name : '-',
        description: tx.description || tx.note || '-',
        moneyIn: Math.round(moneyIn),
        moneyOut: Math.round(moneyOut),
        runningBalance: Math.round(running),
      });
    }

    const closing = Math.round(running);
    accountSummaries.push({
      accountId: acc.id,
      accountName: acc.name,
      accountType: acc.type.replace(/_/g, ' ').toUpperCase(),
      provider: acc.provider || '-',
      accountNumber: acc.accountNumber || '',
      periodOpeningBalance: Math.round(opening),
      moneyIn: Math.round(totalMoneyIn),
      moneyOut: Math.round(totalMoneyOut),
      netChange: Math.round(totalMoneyIn - totalMoneyOut),
      periodClosingBalance: closing,
      liveCurrentBalance: Math.round(liveBalances[acc.id] ?? acc.currentBalance ?? 0),
      txCount: entries.length,
      status: acc.status.toUpperCase(),
    });

    accountStatements.push({
      accountId: acc.id,
      accountName: acc.name,
      accountType: acc.type.replace(/_/g, ' ').toUpperCase(),
      provider: acc.provider || '-',
      openingBalance: Math.round(opening),
      closingBalance: closing,
      totalMoneyIn: Math.round(totalMoneyIn),
      totalMoneyOut: Math.round(totalMoneyOut),
      entries,
    });
  }

  // 4. Monthly / Yearly Income & Expense Trend Analysis
  const trendMap = new Map<
    string,
    { label: string; txCount: number; income: number; grossExpense: number; refunds: number; netExpense: number }
  >();

  if (periodType === 'yearly') {
    const yearNum = parseInt(periodKey, 10) || new Date().getFullYear();
    for (let m = 1; m <= 12; m++) {
      const mm = String(m).padStart(2, '0');
      const k = `${yearNum}-${mm}`;
      trendMap.set(k, {
        label: `${MONTH_NAMES_EN[m - 1]} ${yearNum}`,
        txCount: 0,
        income: 0,
        grossExpense: 0,
        refunds: 0,
        netExpense: 0,
      });
    }
  }

  for (const tx of periodTxAsc) {
    const dateStr = (tx.occurredAt || '').substring(0, 10);
    const bucketKey = periodType === 'yearly' ? dateStr.substring(0, 7) : dateStr;
    if (!bucketKey) continue;
    const bucket = trendMap.get(bucketKey) || {
      label: bucketKey,
      txCount: 0,
      income: 0,
      grossExpense: 0,
      refunds: 0,
      netExpense: 0,
    };
    bucket.txCount += 1;
    if (tx.type === 'income') {
      bucket.income += tx.amount;
    } else if (tx.type === 'expense') {
      bucket.grossExpense += tx.amount;
      const refunded = refundsByExpense[tx.id] || tx.refundedAmount || 0;
      bucket.netExpense += Math.max(0, tx.amount - refunded);
    } else if (tx.type === 'refund') {
      bucket.refunds += tx.amount;
    } else if (tx.type === 'transfer' && tx.transferFee) {
      bucket.netExpense += tx.transferFee;
    }
    trendMap.set(bucketKey, bucket);
  }

  const periodTrendAnalysis: PeriodTrendBucket[] = Array.from(trendMap.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([bucketKey, b]) => ({
      bucketKey,
      label: b.label,
      txCount: b.txCount,
      income: Math.round(b.income),
      grossExpense: Math.round(b.grossExpense),
      refunds: Math.round(b.refunds),
      netExpense: Math.round(b.netExpense),
      netCashFlow: Math.round(b.income - b.netExpense),
    }));

  // 5. Person-wise Summary & Detailed Transaction Analysis (handles split lines)
  const personBuckets = new Map<
    string,
    { txCount: number; expense: number; income: number; other: number }
  >();
  const personDetailedTransactions: PersonDetailedTransactionRow[] = [];

  for (const tx of periodTxAsc) {
    const accName = accountMap.get(tx.accountId)?.name || tx.accountId;
    const dateStr = (tx.occurredAt || '').substring(0, 10);

    if (tx.splitLines && tx.splitLines.length > 0 && tx.splitLines.some((s) => s.personId)) {
      const rawTotal = tx.amount > 0 ? tx.amount : 1;
      const refunded = refundsByExpense[tx.id] || tx.refundedAmount || 0;
      const effectiveTotal = tx.type === 'expense' ? Math.max(0, tx.amount - refunded) : tx.amount;
      const ratio = effectiveTotal / rawTotal;

      for (const split of tx.splitLines) {
        const pid = split.personId || tx.personId || 'unassigned';
        const pObj = personMap.get(pid);
        const amt = Math.round((split.amount || 0) * ratio);
        const prev = personBuckets.get(pid) || { txCount: 0, expense: 0, income: 0, other: 0 };
        prev.txCount += 1;
        if (tx.type === 'expense') prev.expense += amt;
        else if (tx.type === 'income') prev.income += amt;
        else prev.other += amt;
        personBuckets.set(pid, prev);

        if (pid !== 'unassigned') {
          personDetailedTransactions.push({
            personId: pid,
            personName: pObj?.name || pid,
            relationship: pObj?.relationship || 'General',
            date: dateStr,
            type: tx.type.replace(/_/g, ' ').toUpperCase(),
            accountName: accName,
            categoryName: categoryMap.get(split.categoryId)?.name || split.categoryId || '-',
            amount: amt,
            description: split.note || tx.description || '-',
          });
        }
      }
    } else {
      const pid = tx.personId || 'unassigned';
      const pObj = personMap.get(pid);
      const refunded = refundsByExpense[tx.id] || tx.refundedAmount || 0;
      const amt = tx.type === 'expense' ? Math.max(0, tx.amount - refunded) : tx.amount;

      const prev = personBuckets.get(pid) || { txCount: 0, expense: 0, income: 0, other: 0 };
      prev.txCount += 1;
      if (tx.type === 'expense') prev.expense += amt;
      else if (tx.type === 'income') prev.income += amt;
      else prev.other += amt;
      personBuckets.set(pid, prev);

      if (pid !== 'unassigned') {
        personDetailedTransactions.push({
          personId: pid,
          personName: pObj?.name || pid,
          relationship: pObj?.relationship || 'General',
          date: dateStr,
          type: tx.type.replace(/_/g, ' ').toUpperCase(),
          accountName: accName,
          categoryName: tx.categoryId ? categoryMap.get(tx.categoryId)?.name || tx.categoryId : '-',
          amount: Math.round(amt),
          description: tx.description || tx.note || '-',
        });
      }
    }
  }

  let totalPersonExpense = 0;
  for (const v of personBuckets.values()) totalPersonExpense += v.expense;

  const personSummaries: PersonSummaryRow[] = Array.from(personBuckets.entries())
    .map(([pid, v]) => {
      const pObj = personMap.get(pid);
      const share =
        totalPersonExpense > 0 ? Math.round((v.expense / totalPersonExpense) * 1000) / 10 : 0;
      return {
        personId: pid,
        personName: pObj?.name || (pid === 'unassigned' ? 'Self / Unassigned' : pid),
        personNameBn: pObj?.nameBn || (pid === 'unassigned' ? 'নিজ / নির্ধারিত নয়' : pid),
        relationship: pObj?.relationship || (pid === 'unassigned' ? 'Self' : 'Other'),
        txCount: v.txCount,
        totalExpense: Math.round(v.expense),
        totalIncome: Math.round(v.income),
        totalOtherFlow: Math.round(v.other),
        expenseSharePercent: share,
      };
    })
    .sort((a, b) => b.totalExpense + b.totalOtherFlow - (a.totalExpense + a.totalOtherFlow));

  // 6. Category-wise Summary & Detailed Transaction Analysis (handles split lines)
  const catBuckets = new Map<
    string,
    { categoryId: string; type: 'income' | 'expense'; txCount: number; amount: number }
  >();
  const categoryDetailedTransactions: CategoryDetailedTransactionRow[] = [];

  for (const tx of periodTxAsc) {
    if (tx.type !== 'income' && tx.type !== 'expense') continue;
    const accName = accountMap.get(tx.accountId)?.name || tx.accountId;
    const dateStr = (tx.occurredAt || '').substring(0, 10);
    const refunded = refundsByExpense[tx.id] || tx.refundedAmount || 0;
    const effectiveTotal = tx.type === 'expense' ? Math.max(0, tx.amount - refunded) : tx.amount;

    if (tx.splitLines && tx.splitLines.length > 0) {
      const rawTotal = tx.amount > 0 ? tx.amount : 1;
      const ratio = effectiveTotal / rawTotal;
      for (const split of tx.splitLines) {
        const cid = split.categoryId || 'uncategorized';
        const cObj = categoryMap.get(cid);
        const pObj = split.personId
          ? personMap.get(split.personId)
          : tx.personId
            ? personMap.get(tx.personId)
            : undefined;
        const amt = (split.amount || 0) * ratio;
        const key = `${tx.type}:${cid}`;
        const prev = catBuckets.get(key) || {
          categoryId: cid,
          type: tx.type,
          txCount: 0,
          amount: 0,
        };
        prev.txCount += 1;
        prev.amount += amt;
        catBuckets.set(key, prev);

        categoryDetailedTransactions.push({
          categoryId: cid,
          categoryName: cObj?.name || (cid === 'uncategorized' ? 'Uncategorized' : cid),
          categoryType: tx.type,
          date: dateStr,
          accountName: accName,
          personName: pObj?.name || '-',
          amount: Math.round(amt),
          description: split.note || tx.description || '-',
        });
      }
    } else {
      const cid = tx.categoryId || 'uncategorized';
      const cObj = categoryMap.get(cid);
      const pObj = tx.personId ? personMap.get(tx.personId) : undefined;
      const key = `${tx.type}:${cid}`;
      const prev = catBuckets.get(key) || {
        categoryId: cid,
        type: tx.type,
        txCount: 0,
        amount: 0,
      };
      prev.txCount += 1;
      prev.amount += effectiveTotal;
      catBuckets.set(key, prev);

      categoryDetailedTransactions.push({
        categoryId: cid,
        categoryName: cObj?.name || (cid === 'uncategorized' ? 'Uncategorized' : cid),
        categoryType: tx.type,
        date: dateStr,
        accountName: accName,
        personName: pObj?.name || '-',
        amount: Math.round(effectiveTotal),
        description: tx.description || tx.note || '-',
      });
    }
  }

  let catIncomeTotal = 0;
  let catExpenseTotal = 0;
  for (const b of catBuckets.values()) {
    if (b.type === 'income') catIncomeTotal += b.amount;
    else catExpenseTotal += b.amount;
  }

  const categorySummaries: CategorySummaryRow[] = Array.from(catBuckets.values())
    .map((b) => {
      const cObj = categoryMap.get(b.categoryId);
      const denom = b.type === 'income' ? catIncomeTotal : catExpenseTotal;
      const share = denom > 0 ? Math.round((b.amount / denom) * 1000) / 10 : 0;
      return {
        categoryId: b.categoryId,
        categoryName: cObj?.name || (b.categoryId === 'uncategorized' ? 'Uncategorized' : b.categoryId),
        categoryNameBn: cObj?.nameBn || (b.categoryId === 'uncategorized' ? 'শ্রেণীবিহীন' : b.categoryId),
        type: b.type,
        txCount: b.txCount,
        amount: Math.round(b.amount),
        sharePercent: share,
      };
    })
    .sort((a, b) => {
      if (a.type !== b.type) return a.type === 'expense' ? -1 : 1;
      return b.amount - a.amount;
    });

  // 7. Loan Summary
  const loanMap = new Map(periodAndPriorLoans.map((l) => [l.id, l]));
  const periodRepayments = loanRepayments
    .filter((r) => r.date && r.date >= startDate && r.date <= endDate)
    .map((r) => ({
      id: r.id,
      loanId: r.loanId,
      loanType: r.loanType,
      partyName: loanMap.get(r.loanId)?.personName || r.loanId,
      accountName: accountMap.get(r.accountId)?.name || r.accountId,
      amount: r.amount,
      date: r.date,
      note: r.note || '',
    }));

  const loanRows = periodAndPriorLoans.map((l) => ({
    id: l.id,
    type: l.type,
    personName: l.personName,
    originalAmount: l.originalAmount,
    paidAmount: l.paidAmount,
    remainingAmount: l.remainingAmount,
    startDate: l.startDate,
    dueDate: l.dueDate || '',
    status: l.status.toUpperCase(),
    note: l.note || l.description || '',
  }));

  const givenLoans = periodAndPriorLoans.filter((l) => l.type === 'given');
  const takenLoans = periodAndPriorLoans.filter((l) => l.type === 'taken');

  // 8. Savings, Assets & Liabilities
  const savingsAccounts = accountSummaries.filter((a) =>
    ['SAVINGS ACCOUNT', 'DPS', 'FDR'].includes(a.accountType)
  );
  const activeAssets = assets.filter((a) => !a.deletedAt && (!a.purchaseDate || a.purchaseDate <= endDate));
  const activeLiabilities = liabilities.filter(
    (l) => !l.deletedAt && (!l.startDate || l.startDate <= endDate)
  );

  // 9. Relevant Detailed Transactions (Newest first for ledger display)
  const resolvedTransactions = periodTxAsc
    .slice()
    .reverse()
    .map((t) => {
      const cat = t.categoryId ? categoryMap.get(t.categoryId) : undefined;
      const person = t.personId ? personMap.get(t.personId) : undefined;
      const refunded = refundsByExpense[t.id] || t.refundedAmount || 0;
      return {
        id: t.id,
        date: (t.occurredAt || '').substring(0, 10),
        occurredAt: t.occurredAt || '',
        type: t.type.toUpperCase(),
        amount: t.amount,
        netExpense: t.type === 'expense' ? Math.max(0, t.amount - refunded) : null,
        accountName: accountMap.get(t.accountId)?.name || t.accountId,
        destinationAccountName: t.destinationAccountId
          ? accountMap.get(t.destinationAccountId)?.name || t.destinationAccountId
          : '',
        categoryName: cat ? cat.name : t.categoryId || '',
        personName: person
          ? person.relationship
            ? `${person.name} (${person.relationship})`
            : person.name
          : '',
        paymentMethod: t.paymentMethod || '',
        description: t.description || '',
        note: t.note || '',
        status: t.status || 'posted',
      };
    });

  return {
    metadata: {
      reportTitle:
        periodType === 'monthly'
          ? `Monthly Complete Financial Report - ${labelEn}`
          : `Yearly Complete Financial Report - ${labelEn}`,
      periodType,
      periodKey,
      periodLabelEn: labelEn,
      periodLabelBn: labelBn,
      startDate,
      endDate,
      generatedAtDhaka: formatDhakaDateTime(new Date()),
      ownerName: ownerName || 'Authorized User',
      ownerEmail: ownerEmail || '',
      transactionCount: periodTxAsc.length,
    },
    overallCashFlow: {
      totalIncome: Math.round(totalIncome),
      grossExpense: Math.round(grossExpense),
      totalRefunds: Math.round(totalRefunds),
      transferFeeExpense: Math.round(transferFeeExpense),
      netExpense: Math.round(netExpense),
      netCashFlow: Math.round(netCashFlow),
      savingsRatePercent,
      transferVolume: Math.round(transferVolume),
      loanGivenInPeriod: Math.round(loanGivenInPeriod),
      loanReceivedInPeriod: Math.round(loanReceivedInPeriod),
      loanRepaymentReceivedInPeriod: Math.round(loanRepaymentReceivedInPeriod),
      loanRepaymentPaidInPeriod: Math.round(loanRepaymentPaidInPeriod),
      savingsDepositInPeriod: Math.round(savingsDepositInPeriod),
      savingsWithdrawalInPeriod: Math.round(savingsWithdrawalInPeriod),
    },
    netPosition: {
      availableBalance: Math.round(availableBalance),
      cashBalance: Math.round(cashBal),
      bankBalance: Math.round(bankBal),
      mobileBankingBalance: Math.round(mobileBal),
      totalSavings: Math.round(totalSavings),
      loanGivenOutstanding: Math.round(loanGivenOutstanding),
      loanTakenOutstanding: Math.round(loanTakenOutstanding),
      totalAssets: Math.round(totalAssets),
      totalLiabilities: Math.round(totalLiabilities),
      netWorth: Math.round(netWorth),
    },
    transactionTypeSummary,
    accountSummaries,
    accountStatements,
    periodTrendAnalysis,
    personSummaries,
    personDetailedTransactions,
    categorySummaries,
    categoryDetailedTransactions,
    loanSummary: {
      totalGivenPrincipal: givenLoans.reduce((s, l) => s + l.originalAmount, 0),
      totalGivenPaid: givenLoans.reduce((s, l) => s + l.paidAmount, 0),
      totalGivenOutstanding: Math.round(loanGivenOutstanding),
      totalTakenPrincipal: takenLoans.reduce((s, l) => s + l.originalAmount, 0),
      totalTakenPaid: takenLoans.reduce((s, l) => s + l.paidAmount, 0),
      totalTakenOutstanding: Math.round(loanTakenOutstanding),
      loans: loanRows,
      periodRepayments,
    },
    savingsSummary: {
      totalSavingsBalance: Math.round(totalSavings),
      periodDeposits: Math.round(savingsDepositInPeriod),
      periodWithdrawals: Math.round(savingsWithdrawalInPeriod),
      savingsAccounts,
      savingsProducts,
      savingsGoals,
    },
    assetsAndLiabilities: {
      assets: activeAssets,
      liabilities: activeLiabilities,
      totalPhysicalAssets: activeAssets
        .filter((a) => a.status === 'active')
        .reduce((s, a) => s + (a.currentEstimatedValue || a.purchasePrice || 0), 0),
      totalOtherLiabilities: activeLiabilities
        .filter((l) => l.status === 'active')
        .reduce((s, l) => s + (l.remainingAmount ?? 0), 0),
    },
    transactions: resolvedTransactions,
  };
}
