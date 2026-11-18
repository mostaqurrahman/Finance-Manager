import {
  buildUnifiedFinancialReport,
  getAvailableReportPeriods,
  getUnifiedReportFileName,
} from '../services/unifiedReportEngine';
import { buildUnifiedReportWorkbook } from '../services/exportService';
import { Account, Category, Loan, Person, Transaction } from '../types/finance';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(`FAIL: ${message}`);
  }
}

export function runUnifiedReportEngineTests(): { passed: number; total: number; results: string[] } {
  const results: string[] = [];
  let passed = 0;

  function run(name: string, fn: () => void) {
    try {
      fn();
      passed++;
      results.push(`✓ PASS: ${name}`);
    } catch (e: any) {
      results.push(`✗ FAIL: ${name} -> ${e.message}`);
    }
  }

  const accounts: Account[] = [
    {
      id: 'cash',
      name: 'Personal Cash',
      type: 'cash',
      provider: 'Cash',
      openingBalance: 10000,
      openingBalanceDate: '2026-01-01',
      currentBalance: 10000,
      currency: 'BDT',
      status: 'active',
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 'bkash',
      name: 'bKash Wallet',
      type: 'mobile_banking',
      provider: 'bKash',
      openingBalance: 2000,
      openingBalanceDate: '2026-01-01',
      currentBalance: 2000,
      currency: 'BDT',
      status: 'active',
      createdAt: '',
      updatedAt: '',
    },
  ];

  const categories: Category[] = [
    { id: 'cat-salary', name: 'Salary', nameBn: 'বেতন', type: 'income', icon: 'wallet', color: '#059669', isCustom: false, isArchived: false },
    { id: 'cat-food', name: 'Food & Dining', nameBn: 'খাবার', type: 'expense', icon: 'utensils', color: '#dc2626', isCustom: false, isArchived: false },
    { id: 'cat-family', name: 'Family Care', nameBn: 'পরিবার', type: 'expense', icon: 'home', color: '#2563eb', isCustom: true, isArchived: false },
  ];

  const people: Person[] = [
    { id: 'p-1', name: 'Rahim', nameBn: 'রহিম', relationship: 'Brother', isArchived: false },
  ];

  const transactions: Transaction[] = [
    // October 2026 (pre-period for November 2026)
    {
      id: 'tx-oct-1',
      type: 'income',
      amount: 20000,
      accountId: 'cash',
      categoryId: 'cat-salary',
      paymentMethod: 'Cash',
      occurredAt: '2026-10-05',
      timezone: 'Asia/Dhaka',
      description: 'October Salary',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    // November 2026 transactions
    {
      id: 'tx-nov-1',
      type: 'transfer',
      amount: 5000,
      accountId: 'cash',
      destinationAccountId: 'bkash',
      paymentMethod: 'Cash to MFS',
      occurredAt: '2026-11-02',
      timezone: 'Asia/Dhaka',
      description: 'Cash in to bKash',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 'tx-nov-2',
      type: 'expense',
      amount: 3000,
      accountId: 'bkash',
      categoryId: 'cat-food',
      personId: 'p-1',
      paymentMethod: 'bKash',
      occurredAt: '2026-11-05',
      timezone: 'Asia/Dhaka',
      description: 'Family groceries',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 'tx-nov-3',
      type: 'refund',
      amount: 500,
      accountId: 'bkash',
      linkedTransactionId: 'tx-nov-2',
      paymentMethod: 'bKash',
      occurredAt: '2026-11-06',
      timezone: 'Asia/Dhaka',
      description: 'Grocery item refund',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
  ];

  const loans: Loan[] = [
    {
      id: 'loan-1',
      type: 'given',
      personName: 'Karim',
      originalAmount: 4000,
      paidAmount: 1000,
      remainingAmount: 3000,
      startDate: '2026-11-01',
      dueDate: '2026-12-31',
      status: 'partial',
      createdAt: '',
      updatedAt: '',
    },
  ];

  run('Dynamic monthly & yearly periods sorted newest to oldest without empty months', () => {
    const months = getAvailableReportPeriods(transactions, 'monthly');
    assert(months.length === 2, 'Should list only 2 non-empty months');
    assert(months[0].key === '2026-11' && months[0].labelEn === 'November 2026', 'Newest month first');
    assert(months[1].key === '2026-10' && months[1].labelEn === 'October 2026', 'Older month second');
    assert(months[0].txCount === 3, 'November has 3 transactions');

    const years = getAvailableReportPeriods(transactions, 'yearly');
    assert(years.length === 1 && years[0].key === '2026', 'Only year 2026 listed');
    assert(years[0].txCount === 4, 'Yearly count includes all 4 active transactions');
  });

  run('Unified file naming convention for Monthly and Yearly PDF/XLSX', () => {
    assert(getUnifiedReportFileName('monthly', '2026-11', 'pdf') === 'HisabKhata_Monthly_2026-11.pdf', 'Monthly PDF name');
    assert(getUnifiedReportFileName('monthly', '2026-11', 'xlsx') === 'HisabKhata_Monthly_2026-11.xlsx', 'Monthly XLSX name');
    assert(getUnifiedReportFileName('yearly', '2026', 'pdf') === 'HisabKhata_Yearly_2026.pdf', 'Yearly PDF name');
    assert(getUnifiedReportFileName('yearly', '2026', 'xlsx') === 'HisabKhata_Yearly_2026.xlsx', 'Yearly XLSX name');
  });

  run('November report computes accurate pre-period opening balances, transfers, and refunds', () => {
    const report = buildUnifiedFinancialReport({
      periodType: 'monthly',
      periodKey: '2026-11',
      accounts,
      transactions,
      categories,
      people,
      loans,
    });

    // Overall financials for November: 0 income, 3000 gross expense, 500 refund => 2500 net expense
    assert(report.executiveSummary.periodIncome === 0, 'Nov income should be 0');
    assert(report.executiveSummary.periodGrossExpense === 3000, 'Nov gross expense should be 3000');
    assert(report.executiveSummary.periodRefunds === 500, 'Nov refunds should be 500');
    assert(report.executiveSummary.periodNetExpense === 2500, 'Nov net expense should be 2500');

    // Cash had 10,000 opening + 20,000 Oct income = 30,000 opening for Nov; transfer 5,000 out => 25,000 closing
    const cashSummary = report.accountSummaries.find((a) => a.accountId === 'cash')!;
    assert(cashSummary.periodOpeningBalance === 30000, 'Cash Nov opening balance includes Oct income');
    assert(cashSummary.moneyOut === 5000, 'Cash Nov Money Out reflects transfer');
    assert(cashSummary.periodClosingBalance === 25000, 'Cash Nov closing balance is 25,000');

    // bKash had 2,000 opening + 5,000 transfer in + 500 refund in - 3,000 expense out => 4,500 closing
    const bkashSummary = report.accountSummaries.find((a) => a.accountId === 'bkash')!;
    assert(bkashSummary.periodOpeningBalance === 2000, 'bKash Nov opening balance is 2,000');
    assert(bkashSummary.moneyIn === 5500, 'bKash Money In = 5000 transfer + 500 refund');
    assert(bkashSummary.moneyOut === 3000, 'bKash Money Out = 3000');
    assert(bkashSummary.periodClosingBalance === 4500, 'bKash Nov closing balance is 4,500');

    // Workbook contains all 12 core sheets plus individual account statement sheets
    const wb = buildUnifiedReportWorkbook(report);
    assert(wb.SheetNames.includes('Executive Summary'), 'Has Executive Summary');
    assert(wb.SheetNames.includes('Overall Financial Summary'), 'Has Overall Financial Summary');
    assert(wb.SheetNames.includes('Account Summary'), 'Has Account Summary');
    assert(wb.SheetNames.includes('All Transactions'), 'Has All Transactions');
    assert(wb.SheetNames.includes('Monthly Summary'), 'Has Monthly Summary');
    assert(wb.SheetNames.includes('Person-wise Summary'), 'Has Person-wise Summary');
    assert(wb.SheetNames.includes('Person-wise Breakdown'), 'Has Person-wise Breakdown');
    assert(wb.SheetNames.includes('Category-wise Summary'), 'Has Category-wise Summary');
    assert(wb.SheetNames.includes('Category-wise Breakdown'), 'Has Category-wise Breakdown');
    assert(wb.SheetNames.includes('Loan Summary'), 'Has Loan Summary');
    assert(wb.SheetNames.includes('Savings & Assets'), 'Has Savings & Assets');
    assert(wb.SheetNames.includes('Liabilities'), 'Has Liabilities');
    assert(
      wb.SheetNames.some((s) => s.startsWith('Stmt - ') && s.includes('Personal Cash')),
      'Has individual statement sheet for Personal Cash'
    );
    assert(
      wb.SheetNames.some((s) => s.startsWith('Stmt - ') && s.includes('bKash Wallet')),
      'Has individual statement sheet for bKash Wallet'
    );
  });

  return { passed, total: results.length, results };
}
