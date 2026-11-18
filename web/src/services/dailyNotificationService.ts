import {
  Loan,
  NotificationItem,
  RecurringTransaction,
  Transaction,
} from '../types/finance';
import { FinancialSummary } from './ledgerEngine';

export const DEFAULT_DAILY_REMINDER_TIME = '21:00';

export interface DhakaClock {
  dateStr: string; // YYYY-MM-DD
  timeStr: string; // HH:mm
}

export function getDhakaClock(now: Date = new Date()): DhakaClock {
  const dateFmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Dhaka',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  const timeFmt = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Dhaka',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

  const dateStr = dateFmt.format(now);
  const rawTime = timeFmt.format(now);
  // Normalize "24:xx" if ever returned at midnight
  const timeStr = rawTime.startsWith('24:') ? `00:${rawTime.slice(3)}` : rawTime;
  return { dateStr, timeStr };
}

export function getDailyDigestStorageKey(uid: string): string {
  return `pfm_last_daily_digest_${uid}`;
}

export function getDailyReminderPrefStorageKey(uid: string): string {
  return `pfm_daily_reminder_pref_${uid}`;
}

export function loadLocalDailyReminderPref(uid: string): {
  enabled: boolean;
  time: string;
} {
  try {
    const raw = localStorage.getItem(getDailyReminderPrefStorageKey(uid));
    if (raw) {
      const parsed = JSON.parse(raw);
      return {
        enabled: typeof parsed.enabled === 'boolean' ? parsed.enabled : true,
        time:
          typeof parsed.time === 'string' && /^[0-2][0-9]:[0-5][0-9]$/.test(parsed.time)
            ? parsed.time
            : DEFAULT_DAILY_REMINDER_TIME,
      };
    }
  } catch {
    // Ignore storage read errors
  }
  return { enabled: true, time: DEFAULT_DAILY_REMINDER_TIME };
}

export function saveLocalDailyReminderPref(
  uid: string,
  enabled: boolean,
  time: string
): void {
  try {
    localStorage.setItem(
      getDailyReminderPrefStorageKey(uid),
      JSON.stringify({ enabled, time })
    );
  } catch {
    // Ignore storage write errors
  }
}

export interface DailyDigestCheckParams {
  uid: string;
  enabled: boolean;
  reminderTime: string;
  todayDateStr: string;
  currentTimeStr: string;
  lastSentDate?: string | null;
  existingNotificationIds: string[];
}

export function shouldTriggerDailyDigest({
  uid,
  enabled,
  reminderTime,
  todayDateStr,
  currentTimeStr,
  lastSentDate,
  existingNotificationIds,
}: DailyDigestCheckParams): boolean {
  if (!enabled) return false;

  const expectedId = `daily_digest_${todayDateStr}`;
  if (lastSentDate === todayDateStr) return false;
  if (existingNotificationIds.includes(expectedId)) return false;

  try {
    if (localStorage.getItem(getDailyDigestStorageKey(uid)) === todayDateStr) {
      return false;
    }
  } catch {
    // Ignore storage access errors
  }

  const normalizedReminder = /^[0-2][0-9]:[0-5][0-9]$/.test(reminderTime)
    ? reminderTime
    : DEFAULT_DAILY_REMINDER_TIME;

  return currentTimeStr >= normalizedReminder;
}

export interface BuildDailyDigestParams {
  todayDateStr: string;
  transactions: Transaction[];
  summary: FinancialSummary;
  loans: Loan[];
  recurring: RecurringTransaction[];
  manualTrigger?: boolean;
}

function formatBdtSimple(amount: number): string {
  return `৳${Math.round(amount).toLocaleString('en-US')}`;
}

export function buildDailyFinanceUpdate({
  todayDateStr,
  transactions,
  summary,
  loans,
  recurring,
  manualTrigger = false,
}: BuildDailyDigestParams): NotificationItem {
  const todayTxs = transactions.filter(
    (t) =>
      !t.deletedAt &&
      t.status === 'posted' &&
      typeof t.occurredAt === 'string' &&
      t.occurredAt.startsWith(todayDateStr)
  );

  const todayIncome = todayTxs
    .filter((t) => t.type === 'income')
    .reduce((acc, t) => acc + t.amount, 0);

  const todayExpense = todayTxs
    .filter((t) => t.type === 'expense')
    .reduce((acc, t) => acc + (t.netExpense ?? t.amount), 0);

  const overdueLoansCount = loans.filter(
    (l) =>
      !l.deletedAt &&
      l.remainingAmount > 0 &&
      l.dueDate &&
      l.dueDate <= todayDateStr
  ).length;

  const dueRecurringCount = recurring.filter(
    (r) => r.status === 'active' && r.nextDueDate <= todayDateStr
  ).length;

  const dueExtrasEn: string[] = [];
  const dueExtrasBn: string[] = [];

  if (overdueLoansCount > 0) {
    dueExtrasEn.push(`${overdueLoansCount} loan due`);
    dueExtrasBn.push(`${overdueLoansCount}টি ঋণের সময় হয়েছে`);
  }
  if (dueRecurringCount > 0) {
    dueExtrasEn.push(`${dueRecurringCount} recurring bill due`);
    dueExtrasBn.push(`${dueRecurringCount}টি নিয়মিত বিল বাকি`);
  }

  const extraEn = dueExtrasEn.length > 0 ? ` Alerts: ${dueExtrasEn.join(', ')}.` : '';
  const extraBn = dueExtrasBn.length > 0 ? ` সতর্কতা: ${dueExtrasBn.join(', ')}।` : '';

  const availStr = formatBdtSimple(summary.availableBalance);

  const message =
    todayTxs.length === 0
      ? `No entries logged today yet. Available balance: ${availStr}. Take a moment to update today's income or expenses.${extraEn}`
      : `Today: ${todayTxs.length} entries | Income: ${formatBdtSimple(todayIncome)} | Expense: ${formatBdtSimple(todayExpense)} | Available: ${availStr}.${extraEn}`;

  const messageBn =
    todayTxs.length === 0
      ? `আজকে এখনো কোনো লেনদেন যুক্ত হয়নি। উপলব্ধ ব্যালেন্স: ${availStr}। আজকের আয়-ব্যয় আপডেট করুন।${extraBn}`
      : `আজকের সারসংক্ষেপ: ${todayTxs.length}টি লেনদেন | আয়: ${formatBdtSimple(todayIncome)} | ব্যয়: ${formatBdtSimple(todayExpense)} | ব্যালেন্স: ${availStr}।${extraBn}`;

  const suffix = manualTrigger ? `_${Date.now()}` : '';

  return {
    id: `daily_digest_${todayDateStr}${suffix}`,
    title: `Daily Finance Update (${todayDateStr})`,
    titleBn: `দৈনিক ফাইন্যান্স আপডেট (${todayDateStr})`,
    message,
    messageBn,
    type: 'daily_digest',
    isRead: false,
    link: todayTxs.length === 0 ? '/transactions' : '/',
    createdAt: new Date().toISOString(),
  };
}

export type BrowserNotificationPermissionState = NotificationPermission | 'unsupported';

export function getBrowserNotificationPermission(): BrowserNotificationPermissionState {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  return Notification.permission;
}

export async function requestBrowserNotificationPermission(): Promise<BrowserNotificationPermissionState> {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  try {
    return await Notification.requestPermission();
  } catch {
    return Notification.permission;
  }
}

export async function dispatchBrowserNotification(
  item: NotificationItem,
  language: 'bn' | 'en'
): Promise<boolean> {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return false;
  }
  if (Notification.permission !== 'granted') {
    return false;
  }

  const title = language === 'bn' ? item.titleBn : item.title;
  const body = language === 'bn' ? item.messageBn : item.message;

  try {
    if ('serviceWorker' in navigator) {
      const registration = await navigator.serviceWorker.getRegistration();
      if (registration && typeof registration.showNotification === 'function') {
        await registration.showNotification(title, {
          body,
          tag: item.id,
          data: { link: item.link || '/notifications' },
        });
        return true;
      }
    }
  } catch {
    // Fallback to Notification constructor below
  }

  try {
    const notif = new Notification(title, {
      body,
      tag: item.id,
    });
    notif.onclick = () => {
      window.focus();
      notif.close();
    };
    return true;
  } catch {
    return false;
  }
}
