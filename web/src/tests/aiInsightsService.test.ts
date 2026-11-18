import { prepareInsightsDigest } from '../services/aiInsightsService';
import { Category, Transaction } from '../types/finance';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(`FAIL: ${message}`);
  }
}

export function runAiInsightsServiceTests(): { passed: number; total: number; results: string[] } {
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

  const categories: Category[] = [
    { id: 'cat-food', name: 'Food & Dining', nameBn: 'খাবার', type: 'expense', icon: 'utensils', color: '#dc2626', isCustom: false, isArchived: false },
    { id: 'cat-transport', name: 'Transport', nameBn: 'যাতায়াত', type: 'expense', icon: 'car', color: '#2563eb', isCustom: false, isArchived: false },
    { id: 'cat-salary', name: 'Salary', nameBn: 'বেতন', type: 'income', icon: 'wallet', color: '#059669', isCustom: false, isArchived: false },
  ];

  const txs: Transaction[] = [
    {
      id: 't-inc',
      type: 'income',
      amount: 50000,
      accountId: 'bank',
      categoryId: 'cat-salary',
      paymentMethod: 'Bank',
      occurredAt: '2026-11-01',
      timezone: 'Asia/Dhaka',
      description: 'Monthly Salary',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 't-food',
      type: 'expense',
      amount: 10000,
      accountId: 'bkash',
      categoryId: 'cat-food',
      paymentMethod: 'bKash',
      occurredAt: '2026-11-03',
      timezone: 'Asia/Dhaka',
      description: 'Groceries & Dining',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 't-refund',
      type: 'refund',
      amount: 2000,
      accountId: 'bkash',
      linkedTransactionId: 't-food',
      paymentMethod: 'bKash',
      occurredAt: '2026-11-04',
      timezone: 'Asia/Dhaka',
      description: 'Partial grocery refund',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 't-transfer',
      type: 'transfer',
      amount: 15000,
      accountId: 'bank',
      destinationAccountId: 'bkash',
      paymentMethod: 'Transfer',
      occurredAt: '2026-11-05',
      timezone: 'Asia/Dhaka',
      description: 'Top-up bKash',
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 't-split',
      type: 'expense',
      amount: 4000,
      accountId: 'cash',
      paymentMethod: 'Cash',
      occurredAt: '2026-11-07',
      timezone: 'Asia/Dhaka',
      description: 'Combined shopping',
      splitLines: [
        { categoryId: 'cat-food', amount: 3000 },
        { categoryId: 'cat-transport', amount: 1000 },
      ],
      status: 'posted',
      syncStatus: 'synced',
      revisionNumber: 1,
      createdAt: '',
      updatedAt: '',
    },
  ];

  run('prepareInsightsDigest excludes transfers, deducts linked refunds, and aggregates splits', () => {
    const digest = prepareInsightsDigest({
      transactions: txs,
      categories,
      periodLabel: 'November 2026',
      language: 'en',
      budgets: [{ categoryId: 'cat-food', amount: 12000 }],
    });

    assert(digest.totalIncome === 50000, 'Total income should be 50000');
    assert(digest.totalExpense === 12000, 'Net expense should be (10000 - 2000) + 4000 = 12000');
    assert(digest.netCashFlow === 38000, 'Net cash flow should be 38000');
    assert(digest.categoryBreakdown.length === 2, 'Two expense categories in breakdown');
    assert(digest.categoryBreakdown[0].categoryId === 'cat-food', 'Food is highest spend category');
    assert(digest.categoryBreakdown[0].amount === 11000, 'Food net spend should be 8000 + 3000 = 11000');
    assert(digest.categoryBreakdown[1].amount === 1000, 'Transport spend should be 1000');
    assert(digest.budgetStatuses[0].usagePercent === 92, 'Food budget usage is round(11000/12000*100) = 92');
  });

  return { passed, total: results.length, results };
}
