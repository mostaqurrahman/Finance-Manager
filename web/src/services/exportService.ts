/**
 * Export and Backup Service
 * - Excel (.xlsx) export (transactions + comprehensive multi-sheet financial summary)
 * - PDF export (transactions + executive financial summary document for record-keeping)
 * - Full JSON Backup & Restore with validation
 */

import * as XLSX from 'xlsx';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import {
  Transaction,
  Account,
  Loan,
  LoanRepayment,
  SavingsProduct,
  SavingsGoal,
  Asset,
  Liability,
  UserSettings,
  Category,
  Person,
} from '../types/finance';
import {
  FinancialSummary,
  calculateAccountBalances,
  computeComprehensiveSummary,
} from './ledgerEngine';
import {
  formatBDT,
  formatBangladeshiNumber,
  formatDhakaDateTime,
} from '../i18n/formatters';
import {
  ReportExportFormat,
  UnifiedFinancialReport,
  getUnifiedReportFileName,
} from './unifiedReportEngine';

export interface CategoryBreakdownRow {
  categoryId: string;
  categoryName: string;
  categoryNameBn: string;
  type: 'income' | 'expense';
  amount: number;
  count: number;
  sharePercent: number;
}

export interface FinancialSummaryExportPayload {
  startDate: string;
  endDate: string;
  ownerName?: string | null;
  ownerEmail?: string | null;
  accounts: Account[];
  transactions: Transaction[];
  periodTransactions: Transaction[];
  categories: Category[];
  people: Person[];
  loans: Loan[];
  loanRepayments?: LoanRepayment[];
  savingsProducts: SavingsProduct[];
  savingsGoals?: SavingsGoal[];
  assets: Asset[];
  liabilities: Liability[];
  periodSummary?: FinancialSummary;
  includeTransactionsInPdf?: boolean;
}

/**
 * Formats currency in ASCII-safe BDT with South Asian grouping for jsPDF (Helvetica font)
 */
function formatPdfBDT(amount: number): string {
  const absFormatted = formatBangladeshiNumber(Math.abs(amount));
  return amount < 0 ? `-BDT ${absFormatted}` : `BDT ${absFormatted}`;
}

/**
 * Ensures text passed to jsPDF standard fonts won't render garbled non-Latin-1 glyphs
 */
function toPdfSafeText(primary?: string | null, fallback = '-'): string {
  const candidate = (primary || '').replace(/৳/g, 'BDT ').trim();
  if (!candidate) return fallback;
  // Check if string contains characters outside basic printable Latin-1 range
  const hasUnsupported = /[^\x20-\x7E\xA0-\xFF]/.test(candidate);
  if (!hasUnsupported) return candidate;
  const stripped = candidate.replace(/[^\x20-\x7E\xA0-\xFF]/g, '').replace(/\s+/g, ' ').trim();
  return stripped.length > 0 ? stripped : fallback;
}

/**
 * Computes category-wise income and expense breakdown for a list of period transactions
 */
export function buildCategoryBreakdown(
  periodTransactions: Transaction[],
  categories: Category[]
): CategoryBreakdownRow[] {
  const categoryMap = new Map(categories.map((c) => [c.id, c]));
  const buckets = new Map<
    string,
    { categoryId: string; type: 'income' | 'expense'; amount: number; count: number }
  >();

  for (const tx of periodTransactions) {
    if (tx.deletedAt || tx.status === 'cancelled') continue;
    if (tx.type !== 'income' && tx.type !== 'expense') continue;

    if (tx.splitLines && tx.splitLines.length > 0) {
      // Scale split amounts proportionally if expense had a refund
      const rawTotal = tx.amount > 0 ? tx.amount : 1;
      const effectiveTotal = tx.type === 'expense' ? (tx.netExpense ?? tx.amount) : tx.amount;
      const ratio = effectiveTotal / rawTotal;

      for (const split of tx.splitLines) {
        const catId = split.categoryId || 'uncategorized';
        const key = `${tx.type}:${catId}`;
        const prev = buckets.get(key) || {
          categoryId: catId,
          type: tx.type,
          amount: 0,
          count: 0,
        };
        prev.amount += (split.amount || 0) * ratio;
        prev.count += 1;
        buckets.set(key, prev);
      }
    } else {
      const catId = tx.categoryId || 'uncategorized';
      const key = `${tx.type}:${catId}`;
      const effectiveAmount =
        tx.type === 'expense' ? (tx.netExpense ?? tx.amount) : tx.amount;
      const prev = buckets.get(key) || {
        categoryId: catId,
        type: tx.type,
        amount: 0,
        count: 0,
      };
      prev.amount += effectiveAmount;
      prev.count += 1;
      buckets.set(key, prev);
    }
  }

  let totalIncome = 0;
  let totalExpense = 0;
  for (const b of buckets.values()) {
    if (b.type === 'income') totalIncome += b.amount;
    else totalExpense += b.amount;
  }

  const rows: CategoryBreakdownRow[] = Array.from(buckets.values()).map((b) => {
    const cat = categoryMap.get(b.categoryId);
    const denom = b.type === 'income' ? totalIncome : totalExpense;
    const sharePercent = denom > 0 ? Math.round((b.amount / denom) * 1000) / 10 : 0;

    return {
      categoryId: b.categoryId,
      categoryName: cat?.name || (b.categoryId === 'uncategorized' ? 'Uncategorized' : b.categoryId),
      categoryNameBn: cat?.nameBn || (b.categoryId === 'uncategorized' ? 'শ্রেণীবিহীন' : b.categoryId),
      type: b.type,
      amount: Math.round(b.amount),
      count: b.count,
      sharePercent,
    };
  });

  rows.sort((a, b) => {
    if (a.type !== b.type) return a.type === 'expense' ? -1 : 1;
    return b.amount - a.amount;
  });

  return rows;
}

export function buildTransactionExportRows(
  transactions: Transaction[],
  accounts: Account[],
  categories: Category[],
  people: Person[]
) {
  const accountMap = new Map(accounts.map((a) => [a.id, a.name]));
  const categoryMap = new Map(categories.map((c) => [c.id, c]));
  const personMap = new Map(people.map((p) => [p.id, p]));

  return transactions
    .filter((t) => !t.deletedAt)
    .slice()
    .sort((a, b) => (b.occurredAt || '').localeCompare(a.occurredAt || ''))
    .map((t) => {
      const cat = t.categoryId ? categoryMap.get(t.categoryId) : undefined;
      const person = t.personId ? personMap.get(t.personId) : undefined;
      const categoryLabel = cat
        ? cat.nameBn && cat.nameBn !== cat.name
          ? `${cat.name} (${cat.nameBn})`
          : cat.name || cat.nameBn
        : t.categoryId || '';
      const personLabel = person
        ? person.relationship
          ? `${person.name} (${person.relationship})`
          : person.name
        : t.personId || '';

      return {
        Date: (t.occurredAt || '').substring(0, 10),
        'Date & Time': t.occurredAt || '',
        Type: t.type.toUpperCase(),
        'Amount (BDT)': t.amount,
        'Net Expense (BDT)': t.type === 'expense' ? (t.netExpense ?? t.amount) : '',
        Account: accountMap.get(t.accountId) || t.accountId || '',
        'Destination Account': t.destinationAccountId
          ? accountMap.get(t.destinationAccountId) || t.destinationAccountId
          : '',
        Category: categoryLabel,
        Person: personLabel,
        'Payment Method': t.paymentMethod || '',
        Description: t.description || '',
        Note: t.note || '',
        Status: t.status || 'posted',
      };
    });
}

export function exportTransactionsToCsv(
  transactions: Transaction[],
  accounts: Account[],
  categories: Category[],
  people: Person[],
  dateRangeStr?: string
): string {
  const rows = buildTransactionExportRows(transactions, accounts, categories, people);
  const worksheet = XLSX.utils.json_to_sheet(
    rows.length > 0
      ? rows
      : [
          {
            Date: '',
            'Date & Time': '',
            Type: '',
            'Amount (BDT)': '',
            'Net Expense (BDT)': '',
            Account: '',
            'Destination Account': '',
            Category: '',
            Person: '',
            'Payment Method': '',
            Description: 'No transactions recorded',
            Note: '',
            Status: '',
          },
        ]
  );

  const csvContent = XLSX.utils.sheet_to_csv(worksheet);
  // Prepend UTF-8 BOM so Microsoft Excel and Google Sheets preserve Bengali Unicode characters
  const blob = new Blob(['\uFEFF' + csvContent], {
    type: 'text/csv;charset=utf-8;',
  });
  const fileName = `PFM_Transactions_${dateRangeStr || new Date().toISOString().substring(0, 10)}.csv`;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
  return fileName;
}

export function exportTransactionsToExcel(
  transactions: Transaction[],
  accounts: Account[],
  categories: Category[],
  people: Person[],
  dateRangeStr?: string
): string {
  const rows = buildTransactionExportRows(transactions, accounts, categories, people);
  const worksheet = XLSX.utils.json_to_sheet(
    rows.length > 0
      ? rows
      : [
          {
            Date: '',
            'Date & Time': '',
            Type: '',
            'Amount (BDT)': '',
            'Net Expense (BDT)': '',
            Account: '',
            'Destination Account': '',
            Category: '',
            Person: '',
            'Payment Method': '',
            Description: 'No transactions recorded',
            Note: '',
            Status: '',
          },
        ]
  );
  worksheet['!cols'] = [
    { wch: 13 },
    { wch: 22 },
    { wch: 18 },
    { wch: 15 },
    { wch: 16 },
    { wch: 20 },
    { wch: 20 },
    { wch: 22 },
    { wch: 20 },
    { wch: 16 },
    { wch: 30 },
    { wch: 22 },
    { wch: 12 },
  ];
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Transactions');

  const fileName = `PFM_Transactions_${dateRangeStr || new Date().toISOString().substring(0, 10)}.xlsx`;
  XLSX.writeFile(workbook, fileName);
  return fileName;
}

export function downloadTransactionData(
  format: 'csv' | 'xlsx',
  transactions: Transaction[],
  accounts: Account[],
  categories: Category[],
  people: Person[],
  dateRangeStr?: string
): string {
  if (format === 'csv') {
    return exportTransactionsToCsv(transactions, accounts, categories, people, dateRangeStr);
  }
  return exportTransactionsToExcel(transactions, accounts, categories, people, dateRangeStr);
}

export function exportSummaryToExcel(
  summaryData: Record<string, any>,
  fileName = 'PFM_Financial_Summary.xlsx'
) {
  const rows = Object.entries(summaryData).map(([key, value]) => ({
    Metric: key,
    Value: value,
  }));

  const worksheet = XLSX.utils.json_to_sheet(rows);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Summary');
  XLSX.writeFile(workbook, fileName);
}

/**
 * Exports a complete multi-sheet Excel (.xlsx) workbook containing Financial Summary,
 * Account Balances, Category Breakdown, Loans, Savings/Assets/Liabilities, and Period Transactions.
 */
export function exportFinancialSummaryToExcel(payload: FinancialSummaryExportPayload): string {
  const {
    startDate,
    endDate,
    ownerName,
    ownerEmail,
    accounts,
    transactions,
    periodTransactions,
    categories,
    people,
    loans,
    loanRepayments = [],
    savingsProducts,
    savingsGoals = [],
    assets,
    liabilities,
  } = payload;

  const summary =
    payload.periodSummary ||
    computeComprehensiveSummary({
      accounts,
      transactions,
      loans,
      repayments: loanRepayments,
      savingsProducts,
      assets,
      liabilities,
      dateRange: { startDate, endDate },
    });

  const liveBalances = calculateAccountBalances(accounts, transactions);
  const accountMap = new Map(accounts.map((a) => [a.id, a.name]));
  const categoryMap = new Map(categories.map((c) => [c.id, c]));
  const personMap = new Map(people.map((p) => [p.id, p]));

  const netCashFlow = summary.totalIncome - summary.totalExpense;
  const savingsRate =
    summary.totalIncome > 0
      ? Math.round((netCashFlow / summary.totalIncome) * 1000) / 10
      : 0;

  // Sheet 1: Executive Summary
  const summaryRows = [
    { Section: 'Metadata', Metric: 'Report Title', 'Amount (BDT)': '', Details: 'Personal Finance Manager - Financial Summary' },
    { Section: 'Metadata', Metric: 'Reporting Period', 'Amount (BDT)': '', Details: `${startDate || 'Beginning'} to ${endDate || 'Present'}` },
    { Section: 'Metadata', Metric: 'Generated On (Asia/Dhaka)', 'Amount (BDT)': '', Details: formatDhakaDateTime(new Date()) },
    { Section: 'Metadata', Metric: 'Account Holder', 'Amount (BDT)': '', Details: ownerName ? `${ownerName} (${ownerEmail || ''})` : ownerEmail || 'Authorized User' },
    { Section: 'Metadata', Metric: 'Period Transaction Count', 'Amount (BDT)': periodTransactions.length, Details: 'Active non-cancelled entries in range' },
    { Section: 'Period Cash Flow', Metric: 'Period Total Income', 'Amount (BDT)': summary.totalIncome, Details: formatBDT(summary.totalIncome) },
    { Section: 'Period Cash Flow', Metric: 'Period Total Expense (Net of Refunds)', 'Amount (BDT)': summary.totalExpense, Details: formatBDT(summary.totalExpense) },
    { Section: 'Period Cash Flow', Metric: 'Period Net Savings / Cash Flow', 'Amount (BDT)': netCashFlow, Details: `${formatBDT(netCashFlow)} (${savingsRate}% savings rate)` },
    { Section: 'Liquid Balances', Metric: 'Available Liquid Balance (Cash + Bank + MFS)', 'Amount (BDT)': summary.availableBalance, Details: formatBDT(summary.availableBalance) },
    { Section: 'Liquid Balances', Metric: 'Cash Balance', 'Amount (BDT)': summary.cashBalance, Details: formatBDT(summary.cashBalance) },
    { Section: 'Liquid Balances', Metric: 'Bank Balance', 'Amount (BDT)': summary.bankBalance, Details: formatBDT(summary.bankBalance) },
    { Section: 'Liquid Balances', Metric: 'Mobile Banking Balance', 'Amount (BDT)': summary.mobileBankingBalance, Details: formatBDT(summary.mobileBankingBalance) },
    { Section: 'Savings & Loans', Metric: 'Total Savings & Deposits (DPS/FDR/Savings)', 'Amount (BDT)': summary.totalSavings, Details: formatBDT(summary.totalSavings) },
    { Section: 'Savings & Loans', Metric: 'Loan Given Outstanding (Receivable)', 'Amount (BDT)': summary.loanGivenOutstanding, Details: formatBDT(summary.loanGivenOutstanding) },
    { Section: 'Savings & Loans', Metric: 'Loan Taken Outstanding (Payable)', 'Amount (BDT)': summary.loanTakenOutstanding, Details: formatBDT(summary.loanTakenOutstanding) },
    { Section: 'Balance Sheet', Metric: 'Total Assets', 'Amount (BDT)': summary.totalAssets, Details: formatBDT(summary.totalAssets) },
    { Section: 'Balance Sheet', Metric: 'Total Liabilities', 'Amount (BDT)': summary.totalLiabilities, Details: formatBDT(summary.totalLiabilities) },
    { Section: 'Balance Sheet', Metric: 'Total Net Worth (Assets - Liabilities)', 'Amount (BDT)': summary.netWorth, Details: formatBDT(summary.netWorth) },
  ];

  // Sheet 2: Accounts
  const accountRows = accounts.map((acc) => ({
    'Account Name': acc.name,
    Type: acc.type.replace(/_/g, ' ').toUpperCase(),
    Provider: acc.provider,
    'Account Number': acc.accountNumber || '',
    'Opening Balance (BDT)': acc.openingBalance || 0,
    'Opening Date': acc.openingBalanceDate || '',
    'Current Balance (BDT)': liveBalances[acc.id] ?? acc.currentBalance ?? 0,
    Currency: acc.currency || 'BDT',
    Status: acc.status.toUpperCase(),
  }));

  // Sheet 3: Category Breakdown
  const categoryBreakdown = buildCategoryBreakdown(periodTransactions, categories);
  const categoryRows =
    categoryBreakdown.length > 0
      ? categoryBreakdown.map((c) => ({
          Type: c.type.toUpperCase(),
          'Category (EN)': c.categoryName,
          'Category (BN)': c.categoryNameBn,
          'Transactions Count': c.count,
          'Total Amount (BDT)': c.amount,
          'Share (%)': c.sharePercent,
        }))
      : [
          {
            Type: 'INFO',
            'Category (EN)': 'No income/expense categories in selected period',
            'Category (BN)': '',
            'Transactions Count': 0,
            'Total Amount (BDT)': 0,
            'Share (%)': 0,
          },
        ];

  // Sheet 4: Loans
  const activeLoans = loans.filter((l) => !l.deletedAt);
  const loanRows =
    activeLoans.length > 0
      ? activeLoans.map((l) => ({
          Type: l.type === 'given' ? 'LOAN GIVEN (RECEIVABLE)' : 'LOAN TAKEN (PAYABLE)',
          'Person / Counterparty': l.personName,
          'Original Amount (BDT)': l.originalAmount,
          'Paid / Settled (BDT)': l.paidAmount,
          'Remaining (BDT)': l.remainingAmount,
          Account: accountMap.get(l.accountId) || l.accountId,
          'Start Date': l.startDate,
          'Due Date': l.dueDate || '',
          Status: l.status.toUpperCase(),
          Note: l.note || l.description || '',
        }))
      : [
          {
            Type: 'INFO',
            'Person / Counterparty': 'No active loans recorded',
            'Original Amount (BDT)': 0,
            'Paid / Settled (BDT)': 0,
            'Remaining (BDT)': 0,
            Account: '',
            'Start Date': '',
            'Due Date': '',
            Status: '-',
            Note: '',
          },
        ];

  // Sheet 5: Savings, Assets & Liabilities
  const balanceSheetRows: Array<Record<string, string | number>> = [];
  for (const sp of savingsProducts) {
    balanceSheetRows.push({
      Class: 'Savings Product',
      Name: `${sp.institutionName} - ${sp.productName}`,
      Category: sp.type.toUpperCase(),
      'Principal / Purchase (BDT)': sp.principal || 0,
      'Current Value (BDT)': sp.currentValue || 0,
      'Date / Maturity': sp.maturityDate || sp.startDate || '',
      Status: sp.status.toUpperCase(),
    });
  }
  for (const goal of savingsGoals) {
    balanceSheetRows.push({
      Class: 'Savings Goal',
      Name: goal.name,
      Category: 'GOAL',
      'Principal / Purchase (BDT)': goal.targetAmount || 0,
      'Current Value (BDT)': goal.currentSavedAmount || 0,
      'Date / Maturity': goal.deadline || goal.startDate || '',
      Status: goal.status.toUpperCase(),
    });
  }
  for (const asset of assets.filter((a) => !a.deletedAt)) {
    balanceSheetRows.push({
      Class: 'Physical Asset',
      Name: asset.name,
      Category: asset.category,
      'Principal / Purchase (BDT)': asset.purchasePrice || 0,
      'Current Value (BDT)': asset.currentEstimatedValue || 0,
      'Date / Maturity': asset.purchaseDate || '',
      Status: asset.status.toUpperCase(),
    });
  }
  for (const lib of liabilities.filter((l) => !l.deletedAt)) {
    balanceSheetRows.push({
      Class: 'Other Liability',
      Name: lib.name,
      Category: lib.category,
      'Principal / Purchase (BDT)': lib.originalAmount || 0,
      'Current Value (BDT)': lib.remainingAmount || 0,
      'Date / Maturity': lib.dueDate || lib.startDate || '',
      Status: lib.status.toUpperCase(),
    });
  }
  if (balanceSheetRows.length === 0) {
    balanceSheetRows.push({
      Class: 'INFO',
      Name: 'No additional savings products, physical assets, or liabilities recorded',
      Category: '-',
      'Principal / Purchase (BDT)': 0,
      'Current Value (BDT)': 0,
      'Date / Maturity': '',
      Status: '-',
    });
  }

  // Sheet 6: Period Transactions
  const txRows =
    periodTransactions.length > 0
      ? periodTransactions.map((t) => ({
          'Date & Time': t.occurredAt,
          Type: t.type.toUpperCase(),
          'Amount (BDT)': t.amount,
          'Net Expense (BDT)': t.type === 'expense' ? (t.netExpense ?? t.amount) : '',
          Account: accountMap.get(t.accountId) || t.accountId,
          'Destination Account': t.destinationAccountId
            ? (accountMap.get(t.destinationAccountId) || t.destinationAccountId)
            : '',
          Category: t.categoryId ? (categoryMap.get(t.categoryId)?.name || t.categoryId) : '',
          Person: t.personId ? (personMap.get(t.personId)?.name || t.personId) : '',
          'Payment Method': t.paymentMethod,
          Description: t.description,
          Note: t.note || '',
          Status: t.status,
        }))
      : [
          {
            'Date & Time': '',
            Type: 'INFO',
            'Amount (BDT)': 0,
            'Net Expense (BDT)': '',
            Account: '',
            'Destination Account': '',
            Category: '',
            Person: '',
            'Payment Method': '',
            Description: 'No transactions in selected date range',
            Note: '',
            Status: '-',
          },
        ];

  const workbook = XLSX.utils.book_new();

  const summarySheet = XLSX.utils.json_to_sheet(summaryRows);
  summarySheet['!cols'] = [{ wch: 20 }, { wch: 42 }, { wch: 16 }, { wch: 36 }];
  XLSX.utils.book_append_sheet(workbook, summarySheet, 'Executive Summary');

  const accountsSheet = XLSX.utils.json_to_sheet(accountRows);
  accountsSheet['!cols'] = [{ wch: 24 }, { wch: 18 }, { wch: 16 }, { wch: 18 }, { wch: 20 }, { wch: 14 }, { wch: 20 }, { wch: 10 }, { wch: 12 }];
  XLSX.utils.book_append_sheet(workbook, accountsSheet, 'Accounts');

  const categorySheet = XLSX.utils.json_to_sheet(categoryRows);
  categorySheet['!cols'] = [{ wch: 12 }, { wch: 26 }, { wch: 26 }, { wch: 18 }, { wch: 18 }, { wch: 12 }];
  XLSX.utils.book_append_sheet(workbook, categorySheet, 'Category Breakdown');

  const loansSheet = XLSX.utils.json_to_sheet(loanRows);
  loansSheet['!cols'] = [{ wch: 24 }, { wch: 22 }, { wch: 20 }, { wch: 18 }, { wch: 18 }, { wch: 18 }, { wch: 14 }, { wch: 14 }, { wch: 12 }, { wch: 26 }];
  XLSX.utils.book_append_sheet(workbook, loansSheet, 'Loans');

  const assetsSheet = XLSX.utils.json_to_sheet(balanceSheetRows);
  assetsSheet['!cols'] = [{ wch: 18 }, { wch: 28 }, { wch: 18 }, { wch: 22 }, { wch: 20 }, { wch: 16 }, { wch: 12 }];
  XLSX.utils.book_append_sheet(workbook, assetsSheet, 'Savings & Assets');

  const txSheet = XLSX.utils.json_to_sheet(txRows);
  txSheet['!cols'] = [{ wch: 16 }, { wch: 18 }, { wch: 15 }, { wch: 16 }, { wch: 20 }, { wch: 20 }, { wch: 22 }, { wch: 16 }, { wch: 16 }, { wch: 30 }, { wch: 24 }, { wch: 10 }];
  XLSX.utils.book_append_sheet(workbook, txSheet, 'Transactions');

  const fileName = `PFM_Financial_Summary_${startDate || 'all'}_to_${endDate || 'today'}.xlsx`;
  XLSX.writeFile(workbook, fileName);
  return fileName;
}

/**
 * Exports an executive, multi-section Financial Summary PDF document for record-keeping.
 */
export function exportFinancialSummaryToPdf(payload: FinancialSummaryExportPayload): string {
  const {
    startDate,
    endDate,
    ownerName,
    ownerEmail,
    accounts,
    transactions,
    periodTransactions,
    categories,
    loans,
    loanRepayments = [],
    savingsProducts,
    assets,
    liabilities,
    includeTransactionsInPdf = true,
  } = payload;

  const summary =
    payload.periodSummary ||
    computeComprehensiveSummary({
      accounts,
      transactions,
      loans,
      repayments: loanRepayments,
      savingsProducts,
      assets,
      liabilities,
      dateRange: { startDate, endDate },
    });

  const liveBalances = calculateAccountBalances(accounts, transactions);
  const accountMap = new Map(accounts.map((a) => [a.id, a.name]));
  const categoryMap = new Map(categories.map((c) => [c.id, c]));

  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const getLastY = () =>
    ((doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? 40);

  // Header Banner
  doc.setFillColor(5, 150, 105); // Emerald 600
  doc.rect(0, 0, pageWidth, 32, 'F');

  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(17);
  doc.text('Personal Finance Manager', 14, 13);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  doc.text('Comprehensive Financial Summary & Record-Keeping Statement', 14, 20);

  doc.setFontSize(8.5);
  const periodLabel = `Period: ${startDate || 'Start'} to ${endDate || 'Present'}   |   Generated: ${formatDhakaDateTime(new Date())} (Asia/Dhaka)`;
  doc.text(periodLabel, 14, 27);

  // Sub-header Account Holder metadata bar
  doc.setFillColor(241, 245, 249);
  doc.rect(14, 36, pageWidth - 28, 10, 'F');
  doc.setTextColor(51, 65, 85);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8.5);
  const holderInfo = ownerName
    ? `${toPdfSafeText(ownerName)} (${toPdfSafeText(ownerEmail)})`
    : toPdfSafeText(ownerEmail, 'Authorized Workspace');
  doc.text(`Account Holder: ${holderInfo}`, 17, 42.5);
  doc.setFont('helvetica', 'normal');
  doc.text(`Period Entries: ${periodTransactions.length}`, pageWidth - 17, 42.5, { align: 'right' });

  // Section 1: Executive Financial Summary
  const netCashFlow = summary.totalIncome - summary.totalExpense;
  const savingsRate =
    summary.totalIncome > 0
      ? `${Math.round((netCashFlow / summary.totalIncome) * 1000) / 10}%`
      : '0%';

  doc.setTextColor(15, 23, 42);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text('1. Executive Financial Summary', 14, 54);

  autoTable(doc, {
    startY: 57,
    head: [['Financial Metric', 'Category', 'Amount (BDT)']],
    body: [
      ['Period Total Income', 'Cash Flow (Period)', formatPdfBDT(summary.totalIncome)],
      ['Period Total Expense (Net of Refunds)', 'Cash Flow (Period)', formatPdfBDT(summary.totalExpense)],
      [`Period Net Savings (${savingsRate} rate)`, 'Cash Flow (Period)', formatPdfBDT(netCashFlow)],
      ['Available Liquid Balance (Cash + Bank + MFS)', 'Liquidity', formatPdfBDT(summary.availableBalance)],
      ['  - Cash in Hand', 'Liquidity Split', formatPdfBDT(summary.cashBalance)],
      ['  - Bank Accounts', 'Liquidity Split', formatPdfBDT(summary.bankBalance)],
      ['  - Mobile Banking (bKash / Nagad / Rocket)', 'Liquidity Split', formatPdfBDT(summary.mobileBankingBalance)],
      ['Total Savings & Deposits (DPS / FDR)', 'Savings', formatPdfBDT(summary.totalSavings)],
      ['Loan Given Outstanding (Receivable)', 'Asset', formatPdfBDT(summary.loanGivenOutstanding)],
      ['Loan Taken Outstanding (Payable)', 'Liability', formatPdfBDT(summary.loanTakenOutstanding)],
      ['Total Assets', 'Balance Sheet', formatPdfBDT(summary.totalAssets)],
      ['Total Liabilities', 'Balance Sheet', formatPdfBDT(summary.totalLiabilities)],
      ['TOTAL NET WORTH (Assets - Liabilities)', 'Net Position', formatPdfBDT(summary.netWorth)],
    ],
    theme: 'grid',
    headStyles: { fillColor: [15, 23, 42], textColor: [255, 255, 255], fontSize: 8.5 },
    styles: { fontSize: 8.5, cellPadding: 2.3 },
    columnStyles: {
      0: { cellWidth: 96 },
      1: { cellWidth: 42 },
      2: { halign: 'right', fontStyle: 'bold' },
    },
    didParseCell: (data) => {
      if (data.section === 'body' && data.row.index === 12) {
        data.cell.styles.fillColor = [236, 253, 245];
        data.cell.styles.textColor = [4, 120, 87];
        data.cell.styles.fontStyle = 'bold';
      }
    },
  });

  // Section 2: Accounts Overview
  let nextY = getLastY() + 8;
  if (nextY > 250) {
    doc.addPage();
    nextY = 18;
  }
  doc.setTextColor(15, 23, 42);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text('2. Account Balances Overview', 14, nextY);

  const accountTableRows = accounts.map((acc) => [
    toPdfSafeText(acc.name, acc.id),
    acc.type.replace(/_/g, ' ').toUpperCase(),
    toPdfSafeText(acc.provider, '-'),
    formatPdfBDT(acc.openingBalance || 0),
    formatPdfBDT(liveBalances[acc.id] ?? acc.currentBalance ?? 0),
    acc.status.toUpperCase(),
  ]);

  autoTable(doc, {
    startY: nextY + 3,
    head: [['Account Name', 'Type', 'Provider', 'Opening (BDT)', 'Current (BDT)', 'Status']],
    body:
      accountTableRows.length > 0
        ? accountTableRows
        : [['No accounts configured', '-', '-', 'BDT 0', 'BDT 0', '-']],
    theme: 'striped',
    headStyles: { fillColor: [5, 150, 105], fontSize: 8 },
    styles: { fontSize: 8, cellPadding: 2 },
    columnStyles: {
      3: { halign: 'right' },
      4: { halign: 'right', fontStyle: 'bold' },
      5: { halign: 'center' },
    },
  });

  // Section 3: Category Breakdown
  const categoryBreakdown = buildCategoryBreakdown(periodTransactions, categories);
  nextY = getLastY() + 8;
  if (nextY > 250) {
    doc.addPage();
    nextY = 18;
  }
  doc.setTextColor(15, 23, 42);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text('3. Category-wise Income & Expense Breakdown (Selected Period)', 14, nextY);

  autoTable(doc, {
    startY: nextY + 3,
    head: [['Type', 'Category', 'Entries', 'Total (BDT)', 'Share']],
    body:
      categoryBreakdown.length > 0
        ? categoryBreakdown.map((c) => [
            c.type.toUpperCase(),
            toPdfSafeText(c.categoryName, c.categoryId),
            String(c.count),
            formatPdfBDT(c.amount),
            `${c.sharePercent}%`,
          ])
        : [['-', 'No category entries in selected period', '0', 'BDT 0', '0%']],
    theme: 'striped',
    headStyles: { fillColor: [30, 41, 59], fontSize: 8 },
    styles: { fontSize: 8, cellPadding: 2 },
    columnStyles: {
      2: { halign: 'center' },
      3: { halign: 'right', fontStyle: 'bold' },
      4: { halign: 'right' },
    },
  });

  // Section 4: Active Loans & Balance Sheet Items
  const activeLoans = loans.filter((l) => !l.deletedAt);
  const activeAssets = assets.filter((a) => !a.deletedAt);
  const activeLiabilities = liabilities.filter((l) => !l.deletedAt);
  if (activeLoans.length > 0 || savingsProducts.length > 0 || activeAssets.length > 0 || activeLiabilities.length > 0) {
    nextY = getLastY() + 8;
    if (nextY > 250) {
      doc.addPage();
      nextY = 18;
    }
    doc.setTextColor(15, 23, 42);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.text('4. Loans, Savings, Assets & Liabilities Snapshot', 14, nextY);

    const portfolioRows: string[][] = [];
    for (const l of activeLoans) {
      portfolioRows.push([
        l.type === 'given' ? 'LOAN GIVEN' : 'LOAN TAKEN',
        toPdfSafeText(l.personName, 'Counterparty'),
        formatPdfBDT(l.originalAmount),
        formatPdfBDT(l.remainingAmount),
        l.status.toUpperCase(),
      ]);
    }
    for (const sp of savingsProducts) {
      portfolioRows.push([
        `SAVINGS (${sp.type.toUpperCase()})`,
        toPdfSafeText(`${sp.institutionName} - ${sp.productName}`, 'Savings Scheme'),
        formatPdfBDT(sp.principal || 0),
        formatPdfBDT(sp.currentValue || 0),
        sp.status.toUpperCase(),
      ]);
    }
    for (const a of activeAssets) {
      portfolioRows.push([
        'PHYSICAL ASSET',
        toPdfSafeText(a.name, a.category),
        formatPdfBDT(a.purchasePrice || 0),
        formatPdfBDT(a.currentEstimatedValue || 0),
        a.status.toUpperCase(),
      ]);
    }
    for (const lib of activeLiabilities) {
      portfolioRows.push([
        'LIABILITY',
        toPdfSafeText(lib.name, lib.category),
        formatPdfBDT(lib.originalAmount || 0),
        formatPdfBDT(lib.remainingAmount || 0),
        lib.status.toUpperCase(),
      ]);
    }

    autoTable(doc, {
      startY: nextY + 3,
      head: [['Item Type', 'Name / Counterparty', 'Initial / Principal', 'Current / Remaining', 'Status']],
      body: portfolioRows,
      theme: 'grid',
      headStyles: { fillColor: [71, 85, 105], fontSize: 8 },
      styles: { fontSize: 8, cellPadding: 2 },
      columnStyles: {
        2: { halign: 'right' },
        3: { halign: 'right', fontStyle: 'bold' },
        4: { halign: 'center' },
      },
    });
  }

  // Section 5: Period Transactions Ledger (optional)
  if (includeTransactionsInPdf) {
    nextY = getLastY() + 8;
    if (nextY > 245) {
      doc.addPage();
      nextY = 18;
    }
    doc.setTextColor(15, 23, 42);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    const ledgerCountNote =
      periodTransactions.length > 300
        ? ` (Showing latest 300 of ${periodTransactions.length} entries; see Excel for complete ledger)`
        : ` (${periodTransactions.length} entries)`;
    doc.text(`5. Period Transactions Ledger${ledgerCountNote}`, 14, nextY);

    const txRows = periodTransactions.slice(0, 300).map((t) => [
      t.occurredAt.substring(0, 10),
      t.type.replace(/_/g, ' ').toUpperCase(),
      formatPdfBDT(t.amount),
      toPdfSafeText(accountMap.get(t.accountId) || t.accountId, '-'),
      toPdfSafeText(t.categoryId ? categoryMap.get(t.categoryId)?.name : '-', '-'),
      toPdfSafeText(t.description, '-').substring(0, 36),
    ]);

    autoTable(doc, {
      startY: nextY + 3,
      head: [['Date', 'Type', 'Amount', 'Account', 'Category', 'Description']],
      body:
        txRows.length > 0
          ? txRows
          : [['-', '-', 'BDT 0', '-', '-', 'No transactions in selected date range']],
      theme: 'grid',
      headStyles: { fillColor: [5, 150, 105], fontSize: 7.5 },
      styles: { fontSize: 7.5, cellPadding: 1.8 },
      columnStyles: {
        2: { halign: 'right', fontStyle: 'bold' },
      },
    });
  }

  // Page Footers across all pages
  const totalPages = doc.getNumberOfPages();
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFontSize(7.5);
    doc.setTextColor(148, 163, 184);
    const pageHeight = doc.internal.pageSize.getHeight();
    doc.text(
      'Personal Finance Manager • Record-Keeping Export',
      14,
      pageHeight - 8
    );
    doc.text(`Page ${i} of ${totalPages}`, pageWidth - 14, pageHeight - 8, {
      align: 'right',
    });
  }

  const fileName = `PFM_Financial_Summary_${startDate || 'all'}_to_${endDate || 'today'}.pdf`;
  doc.save(fileName);
  return fileName;
}

/**
 * Exports both the PDF Financial Summary document and the multi-sheet Excel workbook
 * for complete record-keeping.
 */
export async function exportFinancialSummaryBundle(
  payload: FinancialSummaryExportPayload
): Promise<{ pdfFile: string; excelFile: string }> {
  const pdfFile = exportFinancialSummaryToPdf(payload);
  // Small delay prevents browsers from suppressing rapid dual downloads
  await new Promise((resolve) => setTimeout(resolve, 350));
  const excelFile = exportFinancialSummaryToExcel(payload);
  return { pdfFile, excelFile };
}

export function exportTransactionsToPdf(
  transactions: Transaction[],
  accounts: Account[],
  categories: Category[],
  title = 'Personal Finance Manager - Transactions Report'
) {
  const doc = new jsPDF();
  const accountMap = new Map(accounts.map((a) => [a.id, a.name]));
  const categoryMap = new Map(categories.map((c) => [c.id, c.name]));

  doc.setFontSize(16);
  doc.text(toPdfSafeText(title, 'Personal Finance Manager - Transactions Report'), 14, 18);
  doc.setFontSize(10);
  doc.text(`Generated on: ${formatDhakaDateTime(new Date())}`, 14, 25);

  const tableRows = transactions.slice(0, 200).map((t) => [
    t.occurredAt.substring(0, 10),
    t.type.toUpperCase(),
    formatBDT(t.amount, { hideSymbol: true }),
    toPdfSafeText(accountMap.get(t.accountId) || t.accountId, '-'),
    toPdfSafeText(t.categoryId ? (categoryMap.get(t.categoryId) || t.categoryId) : '-', '-'),
    toPdfSafeText(t.description, '-').substring(0, 30),
  ]);

  autoTable(doc, {
    startY: 32,
    head: [['Date', 'Type', 'Amount (BDT)', 'Account', 'Category', 'Description']],
    body: tableRows,
    theme: 'grid',
    headStyles: { fillColor: [16, 185, 129] },
    styles: { fontSize: 8 },
  });

  doc.save(`PFM_Report_${new Date().toISOString().substring(0, 10)}.pdf`);
}

export interface FullBackupPayload {
  version: string;
  exportedAt: string;
  settings?: UserSettings | null;
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  people: Person[];
  loans: Loan[];
  savingsProducts: SavingsProduct[];
  assets: Asset[];
  liabilities: Liability[];
}

export function createFullJsonBackup(data: FullBackupPayload): void {
  const jsonStr = JSON.stringify(data, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `PFM_Backup_${new Date().toISOString().substring(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

export function validateJsonBackup(parsed: any): { isValid: boolean; error?: string } {
  if (!parsed || typeof parsed !== 'object') {
    return { isValid: false, error: 'File does not contain valid JSON object' };
  }
  if (!Array.isArray(parsed.accounts)) {
    return { isValid: false, error: 'Missing accounts array in backup file' };
  }
  if (!Array.isArray(parsed.transactions)) {
    return { isValid: false, error: 'Missing transactions array in backup file' };
  }
  return { isValid: true };
}

function makeUniqueExcelSheetName(rawTitle: string, usedNames: Set<string>): string {
  const cleaned = rawTitle.replace(/[\\/*?:[\]]/g, ' ').replace(/\s+/g, ' ').trim() || 'Sheet';
  let candidate = cleaned.substring(0, 31);
  let counter = 2;
  while (usedNames.has(candidate.toLowerCase())) {
    const suffix = ` (${counter})`;
    candidate = cleaned.substring(0, 31 - suffix.length) + suffix;
    counter++;
  }
  usedNames.add(candidate.toLowerCase());
  return candidate;
}

function hasUnicodeNonLatin1(text: string): boolean {
  return /[^\x20-\x7E\xA0-\xFF]/.test(text);
}

/**
 * Renders Bengali / Unicode strings using the browser's HarfBuzz-backed 2D Canvas
 * and embeds crisp vector-aligned PNG text into a jsPDF cell when non-Latin-1 glyphs exist.
 */
function renderUnicodeTextToCanvasDataUrl(
  text: string,
  options: {
    fontSizePx: number;
    bold?: boolean;
    color?: string;
    maxWidthPx: number;
    align?: 'left' | 'right' | 'center';
  }
): { dataUrl: string; widthMm: number; heightMm: number } | null {
  if (typeof document === 'undefined') return null;
  try {
    const scale = 3;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;

    const fontWeight = options.bold ? '700' : '500';
    const fontFamily =
      "'Noto Sans Bengali', 'Hind Siliguri', 'SolaimanLipi', system-ui, -apple-system, sans-serif";
    ctx.font = `${fontWeight} ${options.fontSizePx}px ${fontFamily}`;

    const measured = Math.ceil(ctx.measureText(text).width) + 6;
    const widthPx = Math.min(Math.max(24, measured), Math.max(24, Math.floor(options.maxWidthPx)));
    const heightPx = Math.ceil(options.fontSizePx * 1.45);

    canvas.width = widthPx * scale;
    canvas.height = heightPx * scale;

    ctx.scale(scale, scale);
    ctx.font = `${fontWeight} ${options.fontSizePx}px ${fontFamily}`;
    ctx.fillStyle = options.color || '#0F172A';
    ctx.textBaseline = 'middle';

    let x = 2;
    if (options.align === 'right') {
      ctx.textAlign = 'right';
      x = widthPx - 2;
    } else if (options.align === 'center') {
      ctx.textAlign = 'center';
      x = widthPx / 2;
    } else {
      ctx.textAlign = 'left';
    }

    ctx.fillText(text, x, heightPx / 2 + 0.5, widthPx - 4);
    const pxToMm = 0.264583;
    return {
      dataUrl: canvas.toDataURL('image/png'),
      widthMm: widthPx * pxToMm,
      heightMm: heightPx * pxToMm,
    };
  } catch {
    return null;
  }
}

/**
 * Generates ONE complete Excel (.xlsx) workbook for the selected Monthly or Yearly period
 * with all 12 core sheets plus an individual detailed statement sheet for every account.
 */
export function buildUnifiedReportWorkbook(report: UnifiedFinancialReport): XLSX.WorkBook {
  const workbook = XLSX.utils.book_new();
  const usedSheetNames = new Set<string>();

  const appendSheet = (
    name: string,
    rows: Array<Record<string, string | number>>,
    colWidths?: number[]
  ) => {
    const safeName = makeUniqueExcelSheetName(name, usedSheetNames);
    const sheet = XLSX.utils.json_to_sheet(rows.length > 0 ? rows : [{ Info: 'No records in period' }]);
    if (colWidths) {
      sheet['!cols'] = colWidths.map((wch) => ({ wch }));
    }
    XLSX.utils.book_append_sheet(workbook, sheet, safeName);
  };

  const { metadata, overallCashFlow, netPosition } = report;

  // 1. Executive Summary
  appendSheet(
    'Executive Summary',
    [
      { Category: 'Metadata', Metric: 'Report Title', 'Amount (BDT)': '', Details: metadata.reportTitle },
      { Category: 'Metadata', Metric: 'Report Period Type', 'Amount (BDT)': '', Details: metadata.periodType.toUpperCase() },
      { Category: 'Metadata', Metric: 'Selected Period', 'Amount (BDT)': '', Details: `${metadata.periodLabelEn} (${metadata.startDate} to ${metadata.endDate})` },
      { Category: 'Metadata', Metric: 'Account Holder', 'Amount (BDT)': '', Details: metadata.ownerEmail ? `${metadata.ownerName} (${metadata.ownerEmail})` : metadata.ownerName },
      { Category: 'Metadata', Metric: 'Generated (Asia/Dhaka)', 'Amount (BDT)': '', Details: metadata.generatedAtDhaka },
      { Category: 'Metadata', Metric: 'Transactions Included', 'Amount (BDT)': metadata.transactionCount, Details: `${metadata.transactionCount} active entries` },
      { Category: 'Cash Flow', Metric: 'Total Income', 'Amount (BDT)': overallCashFlow.totalIncome, Details: formatBDT(overallCashFlow.totalIncome) },
      { Category: 'Cash Flow', Metric: 'Gross Expense', 'Amount (BDT)': overallCashFlow.grossExpense, Details: formatBDT(overallCashFlow.grossExpense) },
      { Category: 'Cash Flow', Metric: 'Total Refunds', 'Amount (BDT)': overallCashFlow.totalRefunds, Details: formatBDT(overallCashFlow.totalRefunds) },
      { Category: 'Cash Flow', Metric: 'Net Expense', 'Amount (BDT)': overallCashFlow.netExpense, Details: formatBDT(overallCashFlow.netExpense) },
      { Category: 'Cash Flow', Metric: 'Net Cash Flow / Savings', 'Amount (BDT)': overallCashFlow.netCashFlow, Details: `${formatBDT(overallCashFlow.netCashFlow)} (${overallCashFlow.savingsRatePercent}% savings rate)` },
      { Category: 'Liquidity', Metric: 'Available Liquid Balance', 'Amount (BDT)': netPosition.availableBalance, Details: formatBDT(netPosition.availableBalance) },
      { Category: 'Position', Metric: 'Total Savings & DPS/FDR', 'Amount (BDT)': netPosition.totalSavings, Details: formatBDT(netPosition.totalSavings) },
      { Category: 'Position', Metric: 'Loan Receivable (Given)', 'Amount (BDT)': netPosition.loanGivenOutstanding, Details: formatBDT(netPosition.loanGivenOutstanding) },
      { Category: 'Position', Metric: 'Loan Payable (Taken)', 'Amount (BDT)': netPosition.loanTakenOutstanding, Details: formatBDT(netPosition.loanTakenOutstanding) },
      { Category: 'Balance Sheet', Metric: 'Total Assets', 'Amount (BDT)': netPosition.totalAssets, Details: formatBDT(netPosition.totalAssets) },
      { Category: 'Balance Sheet', Metric: 'Total Liabilities', 'Amount (BDT)': netPosition.totalLiabilities, Details: formatBDT(netPosition.totalLiabilities) },
      { Category: 'Balance Sheet', Metric: 'Net Worth (Assets - Liabilities)', 'Amount (BDT)': netPosition.netWorth, Details: formatBDT(netPosition.netWorth) },
    ],
    [18, 36, 18, 42]
  );

  // 2. Overall Financial Summary
  const overallRows: Array<Record<string, string | number>> = [
    { Section: 'Income & Expense', Item: 'Period Total Income', Count: '-', 'Amount (BDT)': overallCashFlow.totalIncome },
    { Section: 'Income & Expense', Item: 'Period Gross Expense', Count: '-', 'Amount (BDT)': overallCashFlow.grossExpense },
    { Section: 'Income & Expense', Item: 'Period Refunds Received', Count: '-', 'Amount (BDT)': overallCashFlow.totalRefunds },
    { Section: 'Income & Expense', Item: 'Transfer Fees (Expense)', Count: '-', 'Amount (BDT)': overallCashFlow.transferFeeExpense },
    { Section: 'Income & Expense', Item: 'Period Net Expense', Count: '-', 'Amount (BDT)': overallCashFlow.netExpense },
    { Section: 'Income & Expense', Item: 'Net Cash Flow', Count: '-', 'Amount (BDT)': overallCashFlow.netCashFlow },
    { Section: 'Movements', Item: 'Internal Transfer Volume (Excluded from Income/Expense)', Count: '-', 'Amount (BDT)': overallCashFlow.transferVolume },
    { Section: 'Movements', Item: 'Loan Given in Period', Count: '-', 'Amount (BDT)': overallCashFlow.loanGivenInPeriod },
    { Section: 'Movements', Item: 'Loan Received in Period', Count: '-', 'Amount (BDT)': overallCashFlow.loanReceivedInPeriod },
    { Section: 'Movements', Item: 'Loan Repayments Received', Count: '-', 'Amount (BDT)': overallCashFlow.loanRepaymentReceivedInPeriod },
    { Section: 'Movements', Item: 'Loan Repayments Paid', Count: '-', 'Amount (BDT)': overallCashFlow.loanRepaymentPaidInPeriod },
    { Section: 'Movements', Item: 'Savings Deposits', Count: '-', 'Amount (BDT)': overallCashFlow.savingsDepositInPeriod },
    { Section: 'Movements', Item: 'Savings Withdrawals', Count: '-', 'Amount (BDT)': overallCashFlow.savingsWithdrawalInPeriod },
    { Section: 'Liquidity Split', Item: 'Cash Balance (Period End)', Count: '-', 'Amount (BDT)': netPosition.cashBalance },
    { Section: 'Liquidity Split', Item: 'Bank Balance (Period End)', Count: '-', 'Amount (BDT)': netPosition.bankBalance },
    { Section: 'Liquidity Split', Item: 'Mobile Banking Balance (Period End)', Count: '-', 'Amount (BDT)': netPosition.mobileBankingBalance },
    ...report.transactionTypeSummary.map((t) => ({
      Section: 'By Transaction Type',
      Item: t.label,
      Count: t.count,
      'Amount (BDT)': t.totalAmount,
    })),
  ];
  appendSheet('Overall Financial Summary', overallRows, [24, 48, 10, 18]);

  // 3. Account Summary
  appendSheet(
    'Account Summary',
    report.accountSummaries.map((a) => ({
      'Account Name': a.accountName,
      Type: a.accountType,
      Provider: a.provider,
      'Account No.': a.accountNumber,
      'Period Opening (BDT)': a.periodOpeningBalance,
      'Money In (BDT)': a.moneyIn,
      'Money Out (BDT)': a.moneyOut,
      'Net Change (BDT)': a.netChange,
      'Period Closing (BDT)': a.periodClosingBalance,
      'Live Current (BDT)': a.liveCurrentBalance,
      'Period Entries': a.txCount,
      Status: a.status,
    })),
    [22, 16, 16, 16, 18, 16, 16, 16, 18, 18, 12, 10]
  );

  // 4. All Transactions
  appendSheet(
    'All Transactions',
    report.transactions.length > 0
      ? report.transactions.map((t) => ({
          Date: t.date,
          'Date & Time': t.occurredAt,
          Type: t.type,
          'Amount (BDT)': t.amount,
          'Net Expense (BDT)': t.netExpense !== null ? t.netExpense : '',
          Account: t.accountName,
          'Destination Account': t.destinationAccountName,
          Category: t.categoryName,
          Person: t.personName,
          'Payment Method': t.paymentMethod,
          Description: t.description,
          Note: t.note,
          Status: t.status,
        }))
      : [{ Info: `No transactions recorded in ${metadata.periodLabelEn}` }],
    [13, 20, 20, 15, 16, 20, 20, 20, 20, 16, 30, 22, 10]
  );

  // 5. Monthly Summary or Yearly Summary
  const trendSheetTitle = metadata.periodType === 'monthly' ? 'Monthly Summary' : 'Yearly Summary';
  appendSheet(
    trendSheetTitle,
    report.periodTrendAnalysis.length > 0
      ? report.periodTrendAnalysis.map((b) => ({
          Period: b.label,
          Key: b.bucketKey,
          Entries: b.txCount,
          'Income (BDT)': b.income,
          'Gross Expense (BDT)': b.grossExpense,
          'Refunds (BDT)': b.refunds,
          'Net Expense (BDT)': b.netExpense,
          'Net Cash Flow (BDT)': b.netCashFlow,
        }))
      : [{ Info: `No activity in ${metadata.periodLabelEn}` }],
    [22, 14, 10, 16, 18, 15, 18, 18]
  );

  // 6. Person-wise Summary
  appendSheet(
    'Person-wise Summary',
    report.personSummaries.length > 0
      ? report.personSummaries.map((p) => ({
          'Person (EN)': p.personName,
          'Person (BN)': p.personNameBn,
          Relationship: p.relationship,
          Entries: p.txCount,
          'Total Expense (BDT)': p.totalExpense,
          'Total Income (BDT)': p.totalIncome,
          'Loans / Other Flow (BDT)': p.totalOtherFlow,
          'Expense Share (%)': p.expenseSharePercent,
        }))
      : [{ Info: 'No person attribution in selected period' }],
    [22, 22, 18, 10, 18, 18, 22, 16]
  );

  // 7. Person-wise Breakdown
  appendSheet(
    'Person-wise Breakdown',
    report.personDetailedTransactions.length > 0
      ? report.personDetailedTransactions.map((p) => ({
          Person: p.personName,
          Relationship: p.relationship,
          Date: p.date,
          Type: p.type,
          Account: p.accountName,
          Category: p.categoryName,
          'Amount (BDT)': p.amount,
          Description: p.description,
        }))
      : [{ Info: 'No person-linked transactions in selected period' }],
    [22, 16, 13, 18, 20, 20, 15, 30]
  );

  // 8. Category-wise Summary
  appendSheet(
    'Category-wise Summary',
    report.categorySummaries.length > 0
      ? report.categorySummaries.map((c) => ({
          Type: c.type.toUpperCase(),
          'Category (EN)': c.categoryName,
          'Category (BN)': c.categoryNameBn,
          Entries: c.txCount,
          'Total Amount (BDT)': c.amount,
          'Share (%)': c.sharePercent,
        }))
      : [{ Info: 'No category income/expense in selected period' }],
    [12, 24, 24, 10, 18, 12]
  );

  // 9. Category-wise Breakdown
  appendSheet(
    'Category-wise Breakdown',
    report.categoryDetailedTransactions.length > 0
      ? report.categoryDetailedTransactions.map((c) => ({
          Type: c.categoryType.toUpperCase(),
          Category: c.categoryName,
          Date: c.date,
          Account: c.accountName,
          Person: c.personName,
          'Amount (BDT)': c.amount,
          Description: c.description,
        }))
      : [{ Info: 'No category transactions in selected period' }],
    [12, 24, 13, 20, 20, 15, 32]
  );

  // 10. Loan Summary
  const loanSheetRows: Array<Record<string, string | number>> = [
    ...report.loanSummary.loans.map((l) => ({
      Record: 'LOAN',
      Direction: l.type === 'given' ? 'GIVEN (RECEIVABLE)' : 'TAKEN (PAYABLE)',
      Party: l.personName,
      'Principal / Repayment (BDT)': l.originalAmount,
      'Paid (BDT)': l.paidAmount,
      'Remaining (BDT)': l.remainingAmount,
      Date: l.startDate,
      'Due Date': l.dueDate,
      Status: l.status,
      Note: l.note,
    })),
    ...report.loanSummary.periodRepayments.map((r) => ({
      Record: 'REPAYMENT IN PERIOD',
      Direction: r.loanType === 'given' ? 'RECEIVED BACK' : 'PAID BACK',
      Party: r.partyName,
      'Principal / Repayment (BDT)': r.amount,
      'Paid (BDT)': r.amount,
      'Remaining (BDT)': '',
      Date: r.date,
      'Due Date': r.accountName,
      Status: 'POSTED',
      Note: r.note,
    })),
  ];
  appendSheet('Loan Summary', loanSheetRows, [20, 22, 22, 22, 16, 16, 13, 16, 12, 24]);

  // 11. Savings & Assets
  const savingsAssetRows: Array<Record<string, string | number>> = [
    ...report.savingsSummary.savingsAccounts.map((sa) => ({
      Class: 'Savings Account',
      Name: sa.accountName,
      Category: sa.accountType,
      'Opening / Target (BDT)': sa.periodOpeningBalance,
      'Closing / Current (BDT)': sa.periodClosingBalance,
      'Period In / Progress': sa.moneyIn,
      'Period Out / Date': sa.moneyOut,
      Status: sa.status,
    })),
    ...report.savingsSummary.savingsProducts.map((sp) => ({
      Class: 'Savings Product',
      Name: `${sp.institutionName} - ${sp.productName}`,
      Category: sp.type.toUpperCase(),
      'Opening / Target (BDT)': sp.principal || 0,
      'Closing / Current (BDT)': sp.currentValue || 0,
      'Period In / Progress': sp.maturityValue || '',
      'Period Out / Date': sp.maturityDate || sp.startDate || '',
      Status: sp.status.toUpperCase(),
    })),
    ...report.savingsSummary.savingsGoals.map((g) => ({
      Class: 'Savings Goal',
      Name: g.name,
      Category: 'GOAL',
      'Opening / Target (BDT)': g.targetAmount || 0,
      'Closing / Current (BDT)': g.currentSavedAmount || 0,
      'Period In / Progress':
        g.targetAmount > 0 ? `${Math.round((g.currentSavedAmount / g.targetAmount) * 100)}%` : '0%',
      'Period Out / Date': g.deadline || g.startDate || '',
      Status: g.status.toUpperCase(),
    })),
    ...report.assetsAndLiabilities.assets.map((a) => ({
      Class: 'Physical Asset',
      Name: a.name,
      Category: a.category.toUpperCase(),
      'Opening / Target (BDT)': a.purchasePrice || 0,
      'Closing / Current (BDT)': a.currentEstimatedValue || 0,
      'Period In / Progress': '',
      'Period Out / Date': a.purchaseDate || '',
      Status: a.status.toUpperCase(),
    })),
  ];
  appendSheet('Savings & Assets', savingsAssetRows, [18, 28, 16, 20, 20, 18, 18, 12]);

  // 12. Liabilities
  const liabilityRows: Array<Record<string, string | number>> = [
    ...report.loanSummary.loans
      .filter((l) => l.type === 'taken')
      .map((l) => ({
        Class: 'Loan Taken (Payable)',
        Name: l.personName,
        Category: 'LOAN PAYABLE',
        'Original (BDT)': l.originalAmount,
        'Paid (BDT)': l.paidAmount,
        'Remaining (BDT)': l.remainingAmount,
        'Start Date': l.startDate,
        'Due Date': l.dueDate,
        Status: l.status,
      })),
    ...report.assetsAndLiabilities.liabilities.map((lib) => ({
      Class: 'Other Liability',
      Name: lib.name,
      Category: lib.category.toUpperCase(),
      'Original (BDT)': lib.originalAmount || 0,
      'Paid (BDT)': lib.paidAmount || 0,
      'Remaining (BDT)': lib.remainingAmount || 0,
      'Start Date': lib.startDate || '',
      'Due Date': lib.dueDate || '',
      Status: lib.status.toUpperCase(),
    })),
  ];
  appendSheet('Liabilities', liabilityRows, [20, 26, 16, 16, 16, 16, 13, 13, 12]);

  // 13+. Individual Detailed Statement Sheet for EVERY Account
  for (const stmt of report.accountStatements) {
    const stmtRows: Array<Record<string, string | number>> = [
      {
        Date: metadata.startDate,
        Type: 'OPENING BALANCE',
        'Counterparty / Detail': `${stmt.accountName} (${stmt.provider})`,
        Category: '-',
        Person: '-',
        Description: `Balance carried forward prior to ${metadata.startDate}`,
        'Money In (BDT)': '',
        'Money Out (BDT)': '',
        'Running Balance (BDT)': stmt.openingBalance,
      },
      ...stmt.entries.map((e) => ({
        Date: e.date,
        Type: e.type,
        'Counterparty / Detail': e.counterpartyOrDetail,
        Category: e.category,
        Person: e.person,
        Description: e.description,
        'Money In (BDT)': e.moneyIn > 0 ? e.moneyIn : '',
        'Money Out (BDT)': e.moneyOut > 0 ? e.moneyOut : '',
        'Running Balance (BDT)': e.runningBalance,
      })),
      {
        Date: metadata.endDate,
        Type: 'CLOSING BALANCE',
        'Counterparty / Detail': `${stmt.entries.length} transaction(s) in period`,
        Category: '-',
        Person: '-',
        Description: `Closing Balance as of ${metadata.endDate}`,
        'Money In (BDT)': stmt.totalMoneyIn,
        'Money Out (BDT)': stmt.totalMoneyOut,
        'Running Balance (BDT)': stmt.closingBalance,
      },
    ];
    appendSheet(
      `Stmt - ${stmt.accountName}`,
      stmtRows,
      [13, 20, 22, 18, 18, 30, 15, 15, 18]
    );
  }

  return workbook;
}

export function exportUnifiedReportToExcel(report: UnifiedFinancialReport): string {
  const workbook = buildUnifiedReportWorkbook(report);
  const fileName = getUnifiedReportFileName(
    report.metadata.periodType,
    report.metadata.periodKey,
    'xlsx'
  );
  XLSX.writeFile(workbook, fileName);
  return fileName;
}

/**
 * Generates ONE complete, multi-page A4 PDF containing all 14 reporting sections
 * plus an individual detailed statement for every account.
 */
export function exportUnifiedReportToPdf(report: UnifiedFinancialReport): string {
  const { metadata, overallCashFlow, netPosition } = report;
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const getLastY = () =>
    ((doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? 45);

  const ensureSpace = (neededMm: number): number => {
    let y = getLastY() + 8;
    if (y + neededMm > pageHeight - 16) {
      doc.addPage();
      y = 18;
    }
    return y;
  };

  const drawText = (
    text: string,
    x: number,
    y: number,
    opts?: { sizePt?: number; bold?: boolean; color?: string; maxWidthMm?: number; align?: 'left' | 'right' }
  ) => {
    const sizePt = opts?.sizePt ?? 9.5;
    if (hasUnicodeNonLatin1(text)) {
      const rendered = renderUnicodeTextToCanvasDataUrl(text, {
        fontSizePx: sizePt * 1.33,
        bold: opts?.bold,
        color: opts?.color || '#0F172A',
        maxWidthPx: (opts?.maxWidthMm ?? 175) * 3.78,
        align: opts?.align || 'left',
      });
      if (rendered) {
        const drawX = opts?.align === 'right' ? x - rendered.widthMm : x;
        doc.addImage(rendered.dataUrl, 'PNG', drawX, y - rendered.heightMm * 0.72, rendered.widthMm, rendered.heightMm);
        return;
      }
    }
    doc.setFont('helvetica', opts?.bold ? 'bold' : 'normal');
    doc.setFontSize(sizePt);
    doc.text(toPdfSafeText(text, '-'), x, y, opts?.align === 'right' ? { align: 'right' } : undefined);
  };

  const renderSectionTitle = (title: string, y: number) => {
    doc.setTextColor(15, 23, 42);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.text(title, 14, y);
    doc.setDrawColor(226, 232, 240);
    doc.line(14, y + 1.5, pageWidth - 14, y + 1.5);
  };

  // AutoTable Bengali Unicode cell renderer hook
  const unicodeTableHooks = {
    didParseCell: (data: any) => {
      const raw = String(data.cell.raw ?? '');
      if (hasUnicodeNonLatin1(raw)) {
        data.cell.text = ['']; // Suppress garbled Helvetica glyphs; painted cleanly in didDrawCell
      }
    },
    didDrawCell: (data: any) => {
      const raw = String(data.cell.raw ?? '');
      if (!raw || !hasUnicodeNonLatin1(raw)) return;
      const isHeader = data.section === 'head';
      const pad = data.cell.padding('left') || 1.8;
      const maxW = Math.max(10, data.cell.width - pad * 2);
      const rendered = renderUnicodeTextToCanvasDataUrl(raw, {
        fontSizePx: (data.cell.styles.fontSize || 8) * 1.33,
        bold: isHeader || data.cell.styles.fontStyle === 'bold',
        color: isHeader ? '#FFFFFF' : '#0F172A',
        maxWidthPx: maxW * 3.78,
        align: data.cell.styles.halign === 'right' ? 'right' : data.cell.styles.halign === 'center' ? 'center' : 'left',
      });
      if (rendered) {
        const w = Math.min(rendered.widthMm, maxW);
        const h = Math.min(rendered.heightMm, Math.max(3, data.cell.height - 1));
        let drawX = data.cell.x + pad;
        if (data.cell.styles.halign === 'right') {
          drawX = data.cell.x + data.cell.width - pad - w;
        } else if (data.cell.styles.halign === 'center') {
          drawX = data.cell.x + (data.cell.width - w) / 2;
        }
        const drawY = data.cell.y + (data.cell.height - h) / 2;
        doc.addImage(rendered.dataUrl, 'PNG', drawX, drawY, w, h);
      }
    },
  };

  // --- Cover Header Banner ---
  doc.setFillColor(5, 150, 105);
  doc.rect(0, 0, pageWidth, 34, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(17);
  doc.text('HisabKhata • Personal Finance Manager', 14, 13);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10.5);
  doc.text(metadata.reportTitle, 14, 20.5);
  doc.setFontSize(8.5);
  doc.text(
    `Reporting Period: ${metadata.periodLabelEn} (${metadata.startDate} to ${metadata.endDate})  |  Generated: ${metadata.generatedAtDhaka}`,
    14,
    27.5
  );

  // Holder Bar
  doc.setFillColor(241, 245, 249);
  doc.rect(14, 38, pageWidth - 28, 10, 'F');
  doc.setTextColor(51, 65, 85);
  drawText(
    `Holder: ${metadata.ownerName}${metadata.ownerEmail ? ` (${metadata.ownerEmail})` : ''}`,
    17,
    44.5,
    { sizePt: 8.5, bold: true, maxWidthMm: 120 }
  );
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.text(`Total Period Entries: ${metadata.transactionCount}`, pageWidth - 17, 44.5, { align: 'right' });

  // KPI Highlight Cards Row
  const cardW = (pageWidth - 28 - 9) / 4;
  const kpis = [
    { label: 'TOTAL INCOME', val: formatPdfBDT(overallCashFlow.totalIncome), bg: [236, 253, 245], fg: [4, 120, 87] },
    { label: 'NET EXPENSE', val: formatPdfBDT(overallCashFlow.netExpense), bg: [255, 241, 242], fg: [190, 18, 60] },
    { label: 'NET CASH FLOW', val: formatPdfBDT(overallCashFlow.netCashFlow), bg: [239, 246, 255], fg: [29, 78, 216] },
    { label: 'NET WORTH', val: formatPdfBDT(netPosition.netWorth), bg: [240, 253, 250], fg: [15, 118, 110] },
  ];
  kpis.forEach((k, idx) => {
    const cx = 14 + idx * (cardW + 3);
    doc.setFillColor(k.bg[0], k.bg[1], k.bg[2]);
    doc.roundedRect(cx, 51, cardW, 15, 2, 2, 'F');
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(6.8);
    doc.setTextColor(100, 116, 139);
    doc.text(k.label, cx + 3, 56);
    doc.setFontSize(9.5);
    doc.setTextColor(k.fg[0], k.fg[1], k.fg[2]);
    doc.text(k.val, cx + 3, 62.5);
  });

  // Visual Bar Chart: Income vs Net Expense & Liquid Split
  doc.setFillColor(248, 250, 252);
  doc.roundedRect(14, 69, pageWidth - 28, 22, 2, 2, 'F');
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8);
  doc.setTextColor(30, 41, 59);
  doc.text(`Cash Flow Snapshot (${metadata.periodLabelEn})`, 17, 74.5);
  const maxBar = Math.max(1, overallCashFlow.totalIncome, overallCashFlow.netExpense);
  const barMaxW = pageWidth - 85;
  const incW = Math.max(2, (overallCashFlow.totalIncome / maxBar) * barMaxW);
  const expW = Math.max(2, (overallCashFlow.netExpense / maxBar) * barMaxW);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(7.5);
  doc.text('Income', 17, 80.5);
  doc.setFillColor(16, 185, 129);
  doc.roundedRect(36, 77.5, incW, 3.8, 1, 1, 'F');
  doc.text(formatPdfBDT(overallCashFlow.totalIncome), 38 + incW, 80.5);

  doc.text('Expense', 17, 86.5);
  doc.setFillColor(244, 63, 94);
  doc.roundedRect(36, 83.5, expW, 3.8, 1, 1, 'F');
  doc.text(formatPdfBDT(overallCashFlow.netExpense), 38 + expW, 86.5);

  // Section 1 & 2: Executive Summary & Overall Income/Expense/Refund/Cash Flow
  renderSectionTitle('1. Executive Summary & Overall Financial Position', 99);
  autoTable(doc, {
    startY: 102,
    head: [['Metric', 'Section', 'Amount (BDT)']],
    body: [
      ['Period Total Income', 'Cash Flow', formatPdfBDT(overallCashFlow.totalIncome)],
      ['Period Gross Expense', 'Cash Flow', formatPdfBDT(overallCashFlow.grossExpense)],
      ['Period Refunds (Deducted from Expense)', 'Cash Flow', formatPdfBDT(overallCashFlow.totalRefunds)],
      ['Transfer Fees (Expense)', 'Cash Flow', formatPdfBDT(overallCashFlow.transferFeeExpense)],
      ['Period Net Expense', 'Cash Flow', formatPdfBDT(overallCashFlow.netExpense)],
      [`Period Net Cash Flow (${overallCashFlow.savingsRatePercent}% rate)`, 'Cash Flow', formatPdfBDT(overallCashFlow.netCashFlow)],
      ['Internal Transfers Volume (Non-Expense)', 'Movements', formatPdfBDT(overallCashFlow.transferVolume)],
      ['Available Liquid Balance (Period End)', 'Liquidity', formatPdfBDT(netPosition.availableBalance)],
      ['  - Cash / Bank / MFS Split', 'Liquidity', `${formatPdfBDT(netPosition.cashBalance)} / ${formatPdfBDT(netPosition.bankBalance)} / ${formatPdfBDT(netPosition.mobileBankingBalance)}`],
      ['Total Savings & DPS/FDR', 'Savings', formatPdfBDT(netPosition.totalSavings)],
      ['Loan Given Outstanding (Receivable)', 'Asset', formatPdfBDT(netPosition.loanGivenOutstanding)],
      ['Loan Taken Outstanding (Payable)', 'Liability', formatPdfBDT(netPosition.loanTakenOutstanding)],
      ['Total Assets', 'Balance Sheet', formatPdfBDT(netPosition.totalAssets)],
      ['Total Liabilities', 'Balance Sheet', formatPdfBDT(netPosition.totalLiabilities)],
      ['TOTAL NET WORTH (Assets - Liabilities)', 'Net Worth', formatPdfBDT(netPosition.netWorth)],
    ],
    theme: 'grid',
    headStyles: { fillColor: [15, 23, 42], fontSize: 8 },
    styles: { fontSize: 7.8, cellPadding: 2 },
    columnStyles: { 2: { halign: 'right', fontStyle: 'bold' } },
    ...unicodeTableHooks,
  });

  // Section 3: Overall Financial Transaction Summary by Type
  let y = ensureSpace(32);
  renderSectionTitle('2. Overall Financial Transaction Summary (By Type)', y);
  autoTable(doc, {
    startY: y + 3,
    head: [['Transaction Type', 'Entries Count', 'Total Amount (BDT)']],
    body:
      report.transactionTypeSummary.length > 0
        ? report.transactionTypeSummary.map((r) => [r.label, String(r.count), formatPdfBDT(r.totalAmount)])
        : [['No active transactions in period', '0', 'BDT 0']],
    theme: 'striped',
    headStyles: { fillColor: [30, 41, 59], fontSize: 8 },
    styles: { fontSize: 7.8, cellPadding: 2 },
    columnStyles: { 1: { halign: 'center' }, 2: { halign: 'right', fontStyle: 'bold' } },
  });

  // Section 4: Account-wise Summary
  y = ensureSpace(38);
  renderSectionTitle(`3. Account-wise Summary (${metadata.periodLabelEn})`, y);
  autoTable(doc, {
    startY: y + 3,
    head: [['Account', 'Type', 'Opening', 'Money In', 'Money Out', 'Closing']],
    body:
      report.accountSummaries.length > 0
        ? report.accountSummaries.map((a) => [
            a.accountName,
            a.accountType,
            formatPdfBDT(a.periodOpeningBalance),
            formatPdfBDT(a.moneyIn),
            formatPdfBDT(a.moneyOut),
            formatPdfBDT(a.periodClosingBalance),
          ])
        : [['No accounts configured', '-', 'BDT 0', 'BDT 0', 'BDT 0', 'BDT 0']],
    theme: 'striped',
    headStyles: { fillColor: [5, 150, 105], fontSize: 7.8 },
    styles: { fontSize: 7.5, cellPadding: 1.9 },
    columnStyles: {
      2: { halign: 'right' },
      3: { halign: 'right' },
      4: { halign: 'right' },
      5: { halign: 'right', fontStyle: 'bold' },
    },
    ...unicodeTableHooks,
  });

  // Section 5: Monthly / Yearly Income & Expense Trend Analysis
  y = ensureSpace(38);
  renderSectionTitle(
    `4. ${metadata.periodType === 'monthly' ? 'Daily' : 'Month-by-Month'} Income & Expense Analysis`,
    y
  );
  autoTable(doc, {
    startY: y + 3,
    head: [['Period', 'Entries', 'Income', 'Gross Expense', 'Refunds', 'Net Expense', 'Net Cash Flow']],
    body:
      report.periodTrendAnalysis.length > 0
        ? report.periodTrendAnalysis.map((b) => [
            b.label,
            String(b.txCount),
            formatPdfBDT(b.income),
            formatPdfBDT(b.grossExpense),
            formatPdfBDT(b.refunds),
            formatPdfBDT(b.netExpense),
            formatPdfBDT(b.netCashFlow),
          ])
        : [['No activity in period', '0', 'BDT 0', 'BDT 0', 'BDT 0', 'BDT 0', 'BDT 0']],
    theme: 'grid',
    headStyles: { fillColor: [15, 118, 110], fontSize: 7.5 },
    styles: { fontSize: 7.3, cellPadding: 1.8 },
    columnStyles: {
      1: { halign: 'center' },
      2: { halign: 'right' },
      3: { halign: 'right' },
      4: { halign: 'right' },
      5: { halign: 'right' },
      6: { halign: 'right', fontStyle: 'bold' },
    },
  });

  // Section 6 & 7: Person-wise Summary & Detailed Analysis
  y = ensureSpace(38);
  renderSectionTitle('5. Person-wise Expense & Financial Summary', y);
  autoTable(doc, {
    startY: y + 3,
    head: [['Person', 'Relationship', 'Entries', 'Expense', 'Income', 'Other Flow', 'Share']],
    body:
      report.personSummaries.length > 0
        ? report.personSummaries.map((p) => [
            p.personNameBn && p.personNameBn !== p.personName ? `${p.personName} (${p.personNameBn})` : p.personName,
            p.relationship,
            String(p.txCount),
            formatPdfBDT(p.totalExpense),
            formatPdfBDT(p.totalIncome),
            formatPdfBDT(p.totalOtherFlow),
            `${p.expenseSharePercent}%`,
          ])
        : [['No person records in period', '-', '0', 'BDT 0', 'BDT 0', 'BDT 0', '0%']],
    theme: 'striped',
    headStyles: { fillColor: [51, 65, 85], fontSize: 7.5 },
    styles: { fontSize: 7.4, cellPadding: 1.8 },
    columnStyles: { 2: { halign: 'center' }, 3: { halign: 'right', fontStyle: 'bold' }, 4: { halign: 'right' }, 5: { halign: 'right' }, 6: { halign: 'right' } },
    ...unicodeTableHooks,
  });

  if (report.personDetailedTransactions.length > 0) {
    y = ensureSpace(32);
    renderSectionTitle('6. Person-wise Detailed Transaction Analysis', y);
    autoTable(doc, {
      startY: y + 3,
      head: [['Person', 'Relation', 'Date', 'Type', 'Category / Account', 'Amount', 'Description']],
      body: report.personDetailedTransactions.slice(0, 250).map((p) => [
        p.personName,
        p.relationship,
        p.date,
        p.type,
        `${p.categoryName} • ${p.accountName}`,
        formatPdfBDT(p.amount),
        p.description.substring(0, 32),
      ]),
      theme: 'grid',
      headStyles: { fillColor: [71, 85, 105], fontSize: 7.2 },
      styles: { fontSize: 7, cellPadding: 1.6 },
      columnStyles: { 5: { halign: 'right', fontStyle: 'bold' } },
      ...unicodeTableHooks,
    });
  }

  // Section 8 & 9: Category-wise Summary & Detailed Analysis
  y = ensureSpace(38);
  renderSectionTitle('7. Category-wise Income & Expense Summary', y);
  autoTable(doc, {
    startY: y + 3,
    head: [['Type', 'Category', 'Entries', 'Total (BDT)', 'Share']],
    body:
      report.categorySummaries.length > 0
        ? report.categorySummaries.map((c) => [
            c.type.toUpperCase(),
            c.categoryNameBn && c.categoryNameBn !== c.categoryName ? `${c.categoryName} (${c.categoryNameBn})` : c.categoryName,
            String(c.txCount),
            formatPdfBDT(c.amount),
            `${c.sharePercent}%`,
          ])
        : [['-', 'No categories in selected period', '0', 'BDT 0', '0%']],
    theme: 'striped',
    headStyles: { fillColor: [30, 41, 59], fontSize: 7.5 },
    styles: { fontSize: 7.5, cellPadding: 1.8 },
    columnStyles: { 2: { halign: 'center' }, 3: { halign: 'right', fontStyle: 'bold' }, 4: { halign: 'right' } },
    ...unicodeTableHooks,
  });

  if (report.categoryDetailedTransactions.length > 0) {
    y = ensureSpace(32);
    renderSectionTitle('8. Category-wise Detailed Transaction Analysis', y);
    autoTable(doc, {
      startY: y + 3,
      head: [['Type', 'Category', 'Date', 'Account', 'Person', 'Amount', 'Description']],
      body: report.categoryDetailedTransactions.slice(0, 250).map((c) => [
        c.categoryType.toUpperCase(),
        c.categoryName,
        c.date,
        c.accountName,
        c.personName,
        formatPdfBDT(c.amount),
        c.description.substring(0, 30),
      ]),
      theme: 'grid',
      headStyles: { fillColor: [51, 65, 85], fontSize: 7.2 },
      styles: { fontSize: 7, cellPadding: 1.6 },
      columnStyles: { 5: { halign: 'right', fontStyle: 'bold' } },
      ...unicodeTableHooks,
    });
  }

  // Section 10: Loans, Savings, Assets & Liabilities
  y = ensureSpace(38);
  renderSectionTitle('9. Loans, Savings, Assets & Liabilities Summary', y);
  const portfolioRows: string[][] = [
    ...report.loanSummary.loans.map((l) => [
      l.type === 'given' ? 'LOAN GIVEN' : 'LOAN TAKEN',
      l.personName,
      formatPdfBDT(l.originalAmount),
      formatPdfBDT(l.remainingAmount),
      l.status,
    ]),
    ...report.savingsSummary.savingsProducts.map((sp) => [
      `SAVINGS (${sp.type.toUpperCase()})`,
      `${sp.institutionName} - ${sp.productName}`,
      formatPdfBDT(sp.principal || 0),
      formatPdfBDT(sp.currentValue || 0),
      sp.status.toUpperCase(),
    ]),
    ...report.savingsSummary.savingsGoals.map((g) => [
      'SAVINGS GOAL',
      g.name,
      formatPdfBDT(g.targetAmount || 0),
      formatPdfBDT(g.currentSavedAmount || 0),
      g.status.toUpperCase(),
    ]),
    ...report.assetsAndLiabilities.assets.map((a) => [
      'ASSET',
      a.name,
      formatPdfBDT(a.purchasePrice || 0),
      formatPdfBDT(a.currentEstimatedValue || 0),
      a.status.toUpperCase(),
    ]),
    ...report.assetsAndLiabilities.liabilities.map((lib) => [
      'LIABILITY',
      lib.name,
      formatPdfBDT(lib.originalAmount || 0),
      formatPdfBDT(lib.remainingAmount || 0),
      lib.status.toUpperCase(),
    ]),
  ];
  autoTable(doc, {
    startY: y + 3,
    head: [['Class', 'Name / Party', 'Principal / Initial', 'Current / Remaining', 'Status']],
    body: portfolioRows.length > 0 ? portfolioRows : [['-', 'No active loans, savings, assets, or liabilities', 'BDT 0', 'BDT 0', '-']],
    theme: 'grid',
    headStyles: { fillColor: [71, 85, 105], fontSize: 7.5 },
    styles: { fontSize: 7.4, cellPadding: 1.8 },
    columnStyles: { 2: { halign: 'right' }, 3: { halign: 'right', fontStyle: 'bold' }, 4: { halign: 'center' } },
    ...unicodeTableHooks,
  });

  // Section 11: Detailed Statements for EVERY Account
  report.accountStatements.forEach((stmt, idx) => {
    y = ensureSpace(40);
    renderSectionTitle(
      `10.${idx + 1} Account Statement: ${stmt.accountName} (${stmt.provider})`,
      y
    );
    const stmtBody = [
      [
        metadata.startDate,
        'OPENING BALANCE',
        stmt.provider,
        `Carried forward prior to ${metadata.startDate}`,
        '-',
        '-',
        formatPdfBDT(stmt.openingBalance),
      ],
      ...stmt.entries.map((e) => [
        e.date,
        e.type,
        e.counterpartyOrDetail,
        e.description.substring(0, 32),
        e.moneyIn > 0 ? formatPdfBDT(e.moneyIn) : '-',
        e.moneyOut > 0 ? formatPdfBDT(e.moneyOut) : '-',
        formatPdfBDT(e.runningBalance),
      ]),
      [
        metadata.endDate,
        'CLOSING BALANCE',
        `${stmt.entries.length} entries`,
        `Period closing balance`,
        formatPdfBDT(stmt.totalMoneyIn),
        formatPdfBDT(stmt.totalMoneyOut),
        formatPdfBDT(stmt.closingBalance),
      ],
    ];
    autoTable(doc, {
      startY: y + 3,
      head: [['Date', 'Type', 'Counterparty / Detail', 'Description', 'Money In', 'Money Out', 'Balance']],
      body: stmtBody,
      theme: 'grid',
      headStyles: { fillColor: [5, 150, 105], fontSize: 7 },
      styles: { fontSize: 6.9, cellPadding: 1.6 },
      columnStyles: {
        4: { halign: 'right' },
        5: { halign: 'right' },
        6: { halign: 'right', fontStyle: 'bold' },
      },
      ...unicodeTableHooks,
    });
  });

  // Section 12: Complete Period Transactions Ledger
  y = ensureSpace(38);
  renderSectionTitle(`11. Complete Period Transactions Ledger (${report.transactions.length} entries)`, y);
  autoTable(doc, {
    startY: y + 3,
    head: [['Date', 'Type', 'Amount', 'Account', 'Category', 'Person', 'Description']],
    body:
      report.transactions.length > 0
        ? report.transactions.map((t) => [
            t.date,
            t.type,
            formatPdfBDT(t.amount),
            t.destinationAccountName ? `${t.accountName} -> ${t.destinationAccountName}` : t.accountName,
            t.categoryName || '-',
            t.personName || '-',
            t.description.substring(0, 32),
          ])
        : [['-', '-', 'BDT 0', '-', '-', '-', 'No transactions in selected period']],
    theme: 'grid',
    headStyles: { fillColor: [15, 23, 42], fontSize: 7 },
    styles: { fontSize: 6.8, cellPadding: 1.5 },
    columnStyles: { 2: { halign: 'right', fontStyle: 'bold' } },
    ...unicodeTableHooks,
  });

  // Page Footers with Reporting Period & Page Numbers
  const totalPages = doc.getNumberOfPages();
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFontSize(7.5);
    doc.setTextColor(148, 163, 184);
    doc.text(
      `HisabKhata • ${metadata.periodLabelEn} (${metadata.startDate} to ${metadata.endDate})`,
      14,
      pageHeight - 8
    );
    doc.text(`Page ${i} of ${totalPages}`, pageWidth - 14, pageHeight - 8, { align: 'right' });
  }

  const fileName = getUnifiedReportFileName(metadata.periodType, metadata.periodKey, 'pdf');
  doc.save(fileName);
  return fileName;
}

/**
 * Unified Single-Action Complete Report Export for PDF, Excel (.xlsx), or Both (two separate files).
 */
export async function exportCompleteReport(
  report: UnifiedFinancialReport,
  format: ReportExportFormat
): Promise<string[]> {
  const downloadedFiles: string[] = [];

  if (format === 'pdf' || format === 'both') {
    downloadedFiles.push(exportUnifiedReportToPdf(report));
  }

  if (format === 'both') {
    // Allow browser to register the first download cleanly before triggering the second file
    await new Promise((resolve) => setTimeout(resolve, 350));
  }

  if (format === 'xlsx' || format === 'both') {
    downloadedFiles.push(exportUnifiedReportToExcel(report));
  }

  return downloadedFiles;
}

