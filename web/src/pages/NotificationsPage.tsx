import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useFinance } from '../context/FinanceContext';
import { useLanguage } from '../context/LanguageContext';
import { formatDhakaDateTime } from '../i18n/formatters';
import { saveUserSettings } from '../services/dbService';
import {
  DEFAULT_DAILY_REMINDER_TIME,
  getBrowserNotificationPermission,
  loadLocalDailyReminderPref,
  requestBrowserNotificationPermission,
  saveLocalDailyReminderPref,
} from '../services/dailyNotificationService';
import {
  Bell,
  BellRing,
  CalendarClock,
  Check,
  CheckCheck,
  Send,
  Sparkles,
  ArrowUpRight,
} from 'lucide-react';

export const NotificationsPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, settings, setSettingsState } = useAuth();
  const {
    notifications,
    markNotificationRead,
    markAllNotificationsRead,
    triggerDailyDigestNow,
  } = useFinance();
  const { t, language } = useLanguage();

  const localPref = user
    ? loadLocalDailyReminderPref(user.uid)
    : { enabled: true, time: DEFAULT_DAILY_REMINDER_TIME };

  const [dailyEnabled, setDailyEnabled] = useState<boolean>(
    settings?.dailyReminderEnabled ?? localPref.enabled
  );
  const [dailyTime, setDailyTime] = useState<string>(
    settings?.dailyReminderTime ?? localPref.time
  );
  const [browserPerm, setBrowserPerm] = useState(getBrowserNotificationPermission());
  const [isSavingPref, setIsSavingPref] = useState(false);
  const [isSendingNow, setIsSendingNow] = useState(false);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);

  useEffect(() => {
    if (settings) {
      if (typeof settings.dailyReminderEnabled === 'boolean') {
        setDailyEnabled(settings.dailyReminderEnabled);
      }
      if (settings.dailyReminderTime) {
        setDailyTime(settings.dailyReminderTime);
      }
    }
  }, [settings]);

  const sortedNotifications = useMemo(
    () =>
      [...notifications].sort((a, b) =>
        b.createdAt.localeCompare(a.createdAt)
      ),
    [notifications]
  );

  const unreadCount = sortedNotifications.filter((n) => !n.isRead).length;

  const showBanner = (msg: string) => {
    setStatusBanner(msg);
    window.setTimeout(() => setStatusBanner(null), 4000);
  };

  const handleRequestBrowserPermission = async () => {
    const res = await requestBrowserNotificationPermission();
    setBrowserPerm(res);
  };

  const handleSaveReminderPref = async (
    nextEnabled = dailyEnabled,
    nextTime = dailyTime
  ) => {
    if (!user) return;
    setIsSavingPref(true);
    try {
      saveLocalDailyReminderPref(user.uid, nextEnabled, nextTime);
      const updated = {
        language: settings?.language || language,
        currency: 'BDT' as const,
        timezone: settings?.timezone || 'Asia/Dhaka',
        theme: settings?.theme || 'light',
        pinEnabled: settings?.pinEnabled || false,
        ...(settings?.pinHash ? { pinHash: settings.pinHash } : {}),
        ...(settings?.pinSalt ? { pinSalt: settings.pinSalt } : {}),
        passwordEnabled: settings?.passwordEnabled || false,
        autoLockMinutes: settings?.autoLockMinutes ?? 5,
        budgetAlertThresholds: settings?.budgetAlertThresholds || [70, 85, 100],
        isSetupCompleted: settings?.isSetupCompleted ?? true,
        ...(settings?.lastDailyDigestDate
          ? { lastDailyDigestDate: settings.lastDailyDigestDate }
          : {}),
        dailyReminderEnabled: nextEnabled,
        dailyReminderTime: nextTime,
      };
      await saveUserSettings(user.uid, updated);
      setSettingsState(updated);
      showBanner(t('dailyNotifSaved'));
    } catch (err) {
      console.error('Failed saving daily notification setting:', err);
    } finally {
      setIsSavingPref(false);
    }
  };

  const handleSendNow = async () => {
    setIsSendingNow(true);
    try {
      if (browserPerm === 'default') {
        const perm = await requestBrowserNotificationPermission();
        setBrowserPerm(perm);
      }
      await triggerDailyDigestNow();
      showBanner(t('dailyNotifSentNow'));
    } catch (err) {
      console.error('Failed triggering daily finance update:', err);
    } finally {
      setIsSendingNow(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">
            {t('navNotifications')}
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Daily finance update digests, loan repayment dues, budget alerts, and recurring reminders
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={handleSendNow}
            disabled={isSendingNow}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-xs font-semibold shadow-sm"
          >
            <Send className="w-3.5 h-3.5" />
            <span>{isSendingNow ? '...' : t('dailyNotifSendNow')}</span>
          </button>

          {unreadCount > 0 && (
            <button
              type="button"
              onClick={() => markAllNotificationsRead()}
              className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-xs font-semibold text-slate-700 dark:text-slate-200"
            >
              <CheckCheck className="w-3.5 h-3.5 text-emerald-600" />
              <span>{t('dailyNotifMarkAllRead')}</span>
            </button>
          )}
        </div>
      </div>

      {statusBanner && (
        <div className="p-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200 text-xs font-semibold flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-emerald-600 flex-shrink-0" />
          <span>{statusBanner}</span>
        </div>
      )}

      {/* Daily Finance Update Schedule Card */}
      <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400 flex items-center justify-center">
              <CalendarClock className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                  {t('dailyNotifTitle')}
                </h2>
                <span
                  className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                    dailyEnabled
                      ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                      : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400'
                  }`}
                >
                  {dailyEnabled ? `Active • ${dailyTime}` : 'Paused'}
                </span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('dailyNotifDesc')}
              </p>
            </div>
          </div>

          <label className="inline-flex items-center gap-2 cursor-pointer text-xs font-semibold text-slate-700 dark:text-slate-200">
            <input
              type="checkbox"
              checked={dailyEnabled}
              onChange={(e) => {
                const checked = e.target.checked;
                setDailyEnabled(checked);
                handleSaveReminderPref(checked, dailyTime);
              }}
              className="w-5 h-5 accent-emerald-600 rounded cursor-pointer"
            />
            <span>{t('dailyNotifEnable')}</span>
          </label>
        </div>

        <div className="flex flex-wrap items-end justify-between gap-4 pt-2 border-t border-slate-100 dark:border-slate-800">
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="block text-[11px] font-semibold text-slate-500 mb-1">
                {t('dailyNotifTime')}
              </label>
              <input
                type="time"
                value={dailyTime}
                onChange={(e) => setDailyTime(e.target.value)}
                className="px-3 py-1.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs font-bold"
              />
            </div>

            <button
              type="button"
              onClick={() => handleSaveReminderPref(dailyEnabled, dailyTime)}
              disabled={isSavingPref}
              className="px-3.5 py-2 rounded-xl bg-slate-900 dark:bg-slate-800 hover:bg-slate-800 dark:hover:bg-slate-700 text-white text-xs font-semibold"
            >
              {isSavingPref ? '...' : t('save')}
            </button>
          </div>

          <div className="flex items-center gap-2 text-xs">
            <span className="text-slate-500 text-[11px]">
              {t('dailyNotifPermissionLabel')}:
            </span>
            {browserPerm === 'granted' ? (
              <span className="px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300 text-[11px] font-bold">
                Granted
              </span>
            ) : browserPerm === 'denied' ? (
              <span className="px-2.5 py-1 rounded-full bg-rose-100 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300 text-[11px] font-bold">
                Blocked in Browser
              </span>
            ) : browserPerm === 'default' ? (
              <button
                type="button"
                onClick={handleRequestBrowserPermission}
                className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100 text-[11px] font-bold"
              >
                <BellRing className="w-3.5 h-3.5" />
                <span>{t('dailyNotifEnableBrowser')}</span>
              </button>
            ) : (
              <span className="text-[11px] text-slate-400">In-App Only</span>
            )}
          </div>
        </div>
      </div>

      {/* Notifications List */}
      <div className="bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden divide-y divide-slate-100 dark:divide-slate-800">
        {sortedNotifications.length === 0 ? (
          <div className="py-16 text-center text-xs text-slate-400 space-y-2">
            <Bell className="w-8 h-8 mx-auto text-slate-300 dark:text-slate-700" />
            <p>{t('noAlerts')}</p>
          </div>
        ) : (
          sortedNotifications.map((n) => {
            const isDaily = n.type === 'daily_digest';
            return (
              <div
                key={n.id}
                className={`p-4 flex items-center justify-between gap-4 transition-colors ${
                  !n.isRead ? 'bg-emerald-50/40 dark:bg-emerald-950/20' : ''
                }`}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={`w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 ${
                      isDaily
                        ? 'bg-amber-500 text-white'
                        : !n.isRead
                        ? 'bg-emerald-500 text-white'
                        : 'bg-slate-100 text-slate-400 dark:bg-slate-800'
                    }`}
                  >
                    {isDaily ? (
                      <CalendarClock className="w-4 h-4" />
                    ) : (
                      <Bell className="w-4 h-4" />
                    )}
                  </div>
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <h4 className="text-sm font-bold text-slate-900 dark:text-white">
                        {language === 'bn' ? n.titleBn : n.title}
                      </h4>
                      {isDaily && (
                        <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950/70 dark:text-amber-300 text-[10px] font-bold">
                          Daily Update
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-600 dark:text-slate-300 mt-0.5">
                      {language === 'bn' ? n.messageBn : n.message}
                    </p>
                    <div className="flex items-center gap-3 mt-1.5">
                      <span className="text-[10px] text-slate-400">
                        {formatDhakaDateTime(n.createdAt)}
                      </span>
                      {n.link && (
                        <button
                          type="button"
                          onClick={() => navigate(n.link!)}
                          className="inline-flex items-center gap-0.5 text-[11px] font-semibold text-emerald-600 dark:text-emerald-400 hover:underline"
                        >
                          <span>Open</span>
                          <ArrowUpRight className="w-3 h-3" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>

                {!n.isRead && (
                  <button
                    onClick={() => markNotificationRead(n.id)}
                    title="Mark as read"
                    className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-semibold text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 flex-shrink-0"
                  >
                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                    <span>Mark Read</span>
                  </button>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

