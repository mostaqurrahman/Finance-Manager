import {
  buildDailyFinanceUpdate,
  shouldTriggerDailyDigest,
} from '../services/dailyNotificationService';
import { FinancialSummary } from '../services/ledgerEngine';
import { Transaction } from '../types/finance';

function assertEqual<T>(actual: T, expected: T, label: string) {
  if (actual !== expected) {
    throw new Error(`${label}: expected ${String(expected)}, got ${String(actual)}`);
  }
}

function assertTrue(condition: boolean, label: string) {
  if (!condition) {
    throw new Error(`${label}: expected true`);
  }
}

const baseSummary: FinancialSummary = {
  totalIncome: 50000,
  totalExpense: 12000,
  availableBalance: 38000,
  cashBalance: 10000,
  bankBalance: 20000,
  mobileBankingBalance: 8000,
  totalSavings: 15000,
  loanGivenOutstanding: 0,
  loanTakenOutstanding: 0,
  totalAssets: 38000,
  totalLiabilities: 0,
  netWorth: 38000,
};

const sampleTx: Transaction = {
  id: 'tx_1',
  type: 'expense',
  amount: 450,
  accountId: 'acc_cash',
  paymentMethod: 'Cash',
  occurredAt: '2026-11-14',
  timezone: 'Asia/Dhaka',
  description: 'Lunch',
  status: 'posted',
  syncStatus: 'synced',
  revisionNumber: 1,
  createdAt: '2026-11-14T13:00:00.000Z',
  updatedAt: '2026-11-14T13:00:00.000Z',
};

// 1. Trigger check: should trigger at or after reminder time when not sent today
assertEqual(
  shouldTriggerDailyDigest({
    uid: 'u1',
    enabled: true,
    reminderTime: '21:00',
    todayDateStr: '2026-11-14',
    currentTimeStr: '21:05',
    lastSentDate: '2026-11-13',
    existingNotificationIds: [],
  }),
  true,
  'Trigger past reminder time'
);

// 2. Trigger check: should NOT trigger before reminder time
assertEqual(
  shouldTriggerDailyDigest({
    uid: 'u1',
    enabled: true,
    reminderTime: '21:00',
    todayDateStr: '2026-11-14',
    currentTimeStr: '20:59',
    lastSentDate: '2026-11-13',
    existingNotificationIds: [],
  }),
  false,
  'No trigger before reminder time'
);

// 3. Trigger check: deduped when already exists for today
assertEqual(
  shouldTriggerDailyDigest({
    uid: 'u1',
    enabled: true,
    reminderTime: '21:00',
    todayDateStr: '2026-11-14',
    currentTimeStr: '22:00',
    lastSentDate: null,
    existingNotificationIds: ['daily_digest_2026-11-14'],
  }),
  false,
  'Dedupe existing notification'
);

// 4. Digest content with today's transactions
const digestWithTx = buildDailyFinanceUpdate({
  todayDateStr: '2026-11-14',
  transactions: [sampleTx],
  summary: baseSummary,
  loans: [],
  recurring: [],
});

assertEqual(digestWithTx.id, 'daily_digest_2026-11-14', 'Digest ID');
assertEqual(digestWithTx.type, 'daily_digest', 'Digest Type');
assertTrue(digestWithTx.message.includes('1 entries'), 'EN message count');
assertTrue(digestWithTx.message.includes('৳450'), 'EN message expense');
assertTrue(digestWithTx.messageBn.includes('1টি লেনদেন'), 'BN message count');

// 5. Digest content when zero transactions logged today
const digestEmpty = buildDailyFinanceUpdate({
  todayDateStr: '2026-11-14',
  transactions: [],
  summary: baseSummary,
  loans: [],
  recurring: [],
});
assertEqual(digestEmpty.link, '/transactions', 'Empty digest links to transactions');
assertTrue(digestEmpty.message.includes('No entries logged today yet'), 'Empty digest prompt');

console.log('✓ All dailyNotificationService tests passed (5/5)');
