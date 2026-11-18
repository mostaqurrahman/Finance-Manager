import React, { useMemo, useState } from 'react';
import { useFinance } from '../context/FinanceContext';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { formatBDT, formatDhakaDate, getCurrentDhakaDateString } from '../i18n/formatters';
import {
  calculateAccountBalances,
  computeComprehensiveSummary,
  filterTransactionsByDate,
} from '../services/ledgerEngine';
import {
  buildCategoryBreakdown,
  createFullJsonBackup,
  exportTransactionsToCsv,
} from '../services/exportService';
import { ExportReportModal } from '../components/ExportReportModal';
import {
  Calendar,
  CheckCircle2,
  Database,
  Download,
  FileCheck2,
  Landmark,
  PieChart,
  Scale,
  Wallet,
} from 'lucide-react';

export const ReportsPage: React.FC = () => {
  const {
    transactions,
    accounts,
    categories,
    people,
    loans,
    loanRepayments,
    savingsProducts,
    savingsGoals,
    assets,
    liabilities,
  } = useFinance();
  const { user } = useAuth();
  const { t, language } = useLanguage();
  const isBn = language === 'bn';

  const todayStr = getCurrentDhakaDateString();

  const [startDate, setStartDate] = useState(() => todayStr.substring(0, 8) + '01');
  const [endDate, setEndDate] = useState(() => todayStr);
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [exportStatus, setExportStatus] = useState<string | null>(null);

  // Quick date presets
  const applyPreset = (preset: 'this_month' | 'last_month' | 'last_30' | 'this_year' | 'all') => {
    const [yearStr, monthStr] = todayStr.split('-');
    const year = parseInt(yearStr, 10);
    const month = parseInt(monthStr, 10);

    if (preset === 'this_month') {
      setStartDate(`${yearStr}-${monthStr}-01`);
      setEndDate(todayStr);
    } else if (preset === 'last_month') {
      const prevMonthDate = new Date(year, month - 2, 1);
      const lastDayPrevMonth = new Date(year, month - 1, 0);
      const pmYear = prevMonthDate.getFullYear();
      const pmMonth = String(prevMonthDate.getMonth() + 1).padStart(2, '0');
      const ldDay = String(lastDayPrevMonth.getDate()).padStart(2, '0');
      setStartDate(`${pmYear}-${pmMonth}-01`);
      setEndDate(`${pmYear}-${pmMonth}-${ldDay}`);
    } else if (preset === 'last_30') {
      const d = new Date(todayStr);
      d.setDate(d.getDate() - 30);
      setStartDate(d.toISOString().substring(0, 10));
      setEndDate(todayStr);
    } else if (preset === 'this_year') {
      setStartDate(`${yearStr}-01-01`);
      setEndDate(todayStr);
    } else {
      setStartDate('');
      setEndDate('');
    }
  };

  // Authoritative period transactions & financial summary via ledgerEngine
  const periodTx = useMemo(() => {
    return filterTransactionsByDate(transactions, {
      startDate: startDate || undefined,
      endDate: endDate || undefined,
    }).filter((tx) => !tx.deletedAt && tx.status !== 'cancelled');
  }, [transactions, startDate, endDate]);

  const periodSummary = useMemo(() => {
    return computeComprehensiveSummary({
      accounts,
      transactions,
      loans,
      repayments: loanRepayments,
      savingsProducts,
      assets,
      liabilities,
      dateRange: {
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      },
    });
  }, [accounts, transactions, loans, loanRepayments, savingsProducts, assets, liabilities, startDate, endDate]);

  const liveBalances = useMemo(
    () => calculateAccountBalances(accounts, transactions),
    [accounts, transactions]
  );

  const categoryBreakdown = useMemo(
    () => buildCategoryBreakdown(periodTx, categories),
    [periodTx, categories]
  );

  const expenseBreakdown = useMemo(
    () => categoryBreakdown.filter((c) => c.type === 'expense'),
    [categoryBreakdown]
  );

  const netSavings = periodSummary.totalIncome - periodSummary.totalExpense;
  const savingsRate =
    periodSummary.totalIncome > 0
      ? Math.round((netSavings / periodSummary.totalIncome) * 1000) / 10
      : 0;

  const showSuccessToast = (msg: string) => {
    setExportStatus(msg);
    setTimeout(() => setExportStatus(null), 6000);
  };

  const handleExportCsv = () => {
    const file = exportTransactionsToCsv(
      periodTx,
      accounts,
      categories,
      people,
      startDate && endDate ? `${startDate}_to_${endDate}` : undefined
    );
    showSuccessToast(
      isBn
        ? `লেনদেনের তালিকা CSV এক্সপোর্ট সম্পন্ন: ${file}`
        : `Transactions CSV exported: ${file}`
    );
  };

  const handleJsonBackup = () => {
    createFullJsonBackup({
      version: '1.0.0',
      exportedAt: new Date().toISOString(),
      accounts,
      transactions,
      categories,
      people,
      loans,
      savingsProducts,
      assets,
      liabilities,
    });
    showSuccessToast(isBn ? 'JSON ব্যাকআপ ডাউনলোড হয়েছে!' : 'JSON backup downloaded!');
  };

  return (
    <div className="space-y-6">
      {/* Header & Unified Report Export CTA */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">
            {t('navReports')}
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {isBn
              ? 'এক ক্লিকেই নির্দিষ্ট মাস বা বছরের সম্পূর্ণ আর্থিক রিপোর্ট (অ্যাকাউন্ট স্টেটমেন্ট, ব্যক্তি, ক্যাটাগরি, ঋণ ও সঞ্চয়সহ) PDF বা Excel ফাইলে এক্সপোর্ট করুন'
              : 'Export a complete Monthly or Yearly financial report (including all account statements, person, category, loan, and savings summaries) as PDF, Excel, or Both'}
          </p>
        </div>

        {/* One Central Export Report Button + Optional Raw Data Actions */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => setIsExportModalOpen(true)}
            data-testid="open-export-report-btn"
            className="flex items-center gap-2 px-5 py-3 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 text-white text-xs sm:text-sm font-bold shadow-lg shadow-emerald-600/25 transition-all cursor-pointer min-h-[48px]"
          >
            <FileCheck2 className="w-4 h-4" />
            <span>{t('exportReport')}</span>
          </button>

          <button
            onClick={handleExportCsv}
            data-testid="export-transactions-csv-btn"
            className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md shadow-blue-600/20 transition-all cursor-pointer min-h-[44px]"
          >
            <Download className="w-4 h-4" />
            <span>{isBn ? 'CSV (.csv)' : 'Raw CSV (.csv)'}</span>
          </button>

          <button
            onClick={handleJsonBackup}
            data-testid="export-json-backup-btn"
            className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 dark:bg-slate-800 dark:hover:bg-slate-700 text-white text-xs font-semibold shadow-md transition-all cursor-pointer min-h-[44px]"
          >
            <Database className="w-4 h-4 text-emerald-400" />
            <span>{isBn ? 'JSON ব্যাকআপ' : 'JSON Backup'}</span>
          </button>
        </div>
      </div>

      {/* Export Success Banner */}
      {exportStatus && (
        <div className="flex items-center gap-2.5 px-4 py-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/50 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200 text-xs font-semibold">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400 flex-shrink-0" />
          <span>{exportStatus}</span>
        </div>
      )}

      {/* Record-Keeping Package Info & Filters */}
      <div className="p-4 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* Quick Date Presets */}
          <div className="flex flex-wrap items-center gap-1.5">
            <Calendar className="w-4 h-4 text-emerald-600 dark:text-emerald-400 mr-1" />
            {[
              { id: 'this_month', label: isBn ? 'এই মাস' : 'This Month' },
              { id: 'last_month', label: isBn ? 'গত মাস' : 'Last Month' },
              { id: 'last_30', label: isBn ? 'গত ৩০ দিন' : 'Last 30 Days' },
              { id: 'this_year', label: isBn ? 'এই বছর' : 'This Year' },
              { id: 'all', label: isBn ? 'সব সময়' : 'All Time' },
            ].map((p) => (
              <button
                key={p.id}
                onClick={() => applyPreset(p.id as any)}
                className="px-2.5 py-1.5 rounded-xl text-xs font-semibold bg-slate-100 hover:bg-emerald-100 text-slate-700 hover:text-emerald-800 dark:bg-slate-800 dark:hover:bg-emerald-950 dark:text-slate-300 dark:hover:text-emerald-300 transition-colors cursor-pointer"
              >
                {p.label}
              </button>
            ))}
          </div>

          {/* Custom Date Range */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-semibold text-slate-500">{t('filterFrom')}:</span>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="px-2.5 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-xs text-slate-800 dark:text-white"
              />
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-semibold text-slate-500">{t('filterTo')}:</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="px-2.5 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-xs text-slate-800 dark:text-white"
              />
            </div>
          </div>
        </div>

        <div className="pt-2 border-t border-slate-100 dark:border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs">
          <span className="text-slate-600 dark:text-slate-300">
            {isBn
              ? 'সম্পূর্ণ মাসিক বা বার্ষিক রিপোর্ট ডাউনলোড করতে উপরের "রিপোর্ট এক্সপোর্ট" বাটনে ক্লিক করুন।'
              : 'Click "Export Report" above to generate a complete Monthly or Yearly PDF / Excel report with all account statements.'}
          </span>

          <div className="flex items-center gap-2 text-slate-400">
            <Download className="w-3.5 h-3.5 text-emerald-500" />
            <span>
              {isBn
                ? `${periodTx.length}টি লেনদেন বর্তমান ফিল্টারে দেখানো হচ্ছে`
                : `Showing ${periodTx.length} entries in analytics preview`}
            </span>
          </div>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm">
          <span className="text-xs text-slate-400 block mb-1">
            {isBn ? 'নির্বাচিত সময়ের আয়' : 'Period Income'}
          </span>
          <span className="text-xl font-bold text-emerald-600 dark:text-emerald-400">
            {formatBDT(periodSummary.totalIncome, { isBengali: isBn })}
          </span>
        </div>

        <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm">
          <span className="text-xs text-slate-400 block mb-1">
            {isBn ? 'নির্বাচিত সময়ের ব্যয় (নিট)' : 'Period Expense (Net)'}
          </span>
          <span className="text-xl font-bold text-rose-600 dark:text-rose-400">
            {formatBDT(periodSummary.totalExpense, { isBengali: isBn })}
          </span>
        </div>

        <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm">
          <span className="text-xs text-slate-400 block mb-1">
            {isBn ? 'নিট সঞ্চয় / ক্যাশ ফ্লো' : 'Period Net Savings'}
          </span>
          <span
            className={`text-xl font-bold ${
              netSavings >= 0
                ? 'text-slate-900 dark:text-white'
                : 'text-rose-600 dark:text-rose-400'
            }`}
          >
            {formatBDT(netSavings, { isBengali: isBn })}
          </span>
          <span className="text-[11px] text-slate-400 block mt-0.5">
            {isBn ? `সঞ্চয়ের হার: ${savingsRate}%` : `Savings Rate: ${savingsRate}%`}
          </span>
        </div>

        <div className="p-5 rounded-3xl bg-gradient-to-br from-emerald-600 to-teal-700 text-white shadow-md shadow-emerald-600/20">
          <span className="text-xs text-emerald-100 block mb-1">
            {t('netWorth')}
          </span>
          <span className="text-xl font-bold">
            {formatBDT(periodSummary.netWorth, { isBengali: isBn })}
          </span>
          <span className="text-[11px] text-emerald-100/90 block mt-0.5">
            {isBn ? 'সম্পদ' : 'Assets'}: {formatBDT(periodSummary.totalAssets, { isBengali: isBn })}
          </span>
        </div>
      </div>

      {/* 2-Column Financial Summary Breakdown Preview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Account Balances & Liquidity */}
        <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Wallet className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <h3 className="text-sm font-bold text-slate-800 dark:text-white">
                {isBn ? 'অ্যাকাউন্ট ও তারল্য সারসংক্ষেপ' : 'Accounts & Liquidity Summary'}
              </h3>
            </div>
            <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400">
              {formatBDT(periodSummary.availableBalance, { isBengali: isBn })}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="p-2.5 rounded-2xl bg-slate-50 dark:bg-slate-800/60">
              <span className="text-[11px] text-slate-400 block">{t('cashBalance')}</span>
              <span className="text-xs font-bold text-slate-800 dark:text-white">
                {formatBDT(periodSummary.cashBalance, { isBengali: isBn })}
              </span>
            </div>
            <div className="p-2.5 rounded-2xl bg-slate-50 dark:bg-slate-800/60">
              <span className="text-[11px] text-slate-400 block">{t('bankBalance')}</span>
              <span className="text-xs font-bold text-slate-800 dark:text-white">
                {formatBDT(periodSummary.bankBalance, { isBengali: isBn })}
              </span>
            </div>
            <div className="p-2.5 rounded-2xl bg-slate-50 dark:bg-slate-800/60">
              <span className="text-[11px] text-slate-400 block">{isBn ? 'মোবাইল ব্যাংকিং' : 'MFS'}</span>
              <span className="text-xs font-bold text-slate-800 dark:text-white">
                {formatBDT(periodSummary.mobileBankingBalance, { isBengali: isBn })}
              </span>
            </div>
          </div>

          <div className="divide-y divide-slate-100 dark:divide-slate-800 text-xs">
            {accounts.map((acc) => {
              const bal = liveBalances[acc.id] ?? acc.currentBalance ?? 0;
              return (
                <div key={acc.id} className="py-2 flex items-center justify-between">
                  <div>
                    <span className="font-semibold text-slate-800 dark:text-white block">
                      {acc.name}
                    </span>
                    <span className="text-[10px] uppercase text-slate-400">
                      {acc.provider} • {acc.type.replace(/_/g, ' ')}
                    </span>
                  </div>
                  <span className="font-bold text-slate-900 dark:text-white">
                    {formatBDT(bal, { isBengali: isBn })}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Period Category Breakdown */}
        <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <PieChart className="w-4 h-4 text-rose-500" />
              <h3 className="text-sm font-bold text-slate-800 dark:text-white">
                {isBn ? 'ক্যাটাগরিভিত্তিক ব্যয় বিশ্লেষণ' : 'Expense Breakdown by Category'}
              </h3>
            </div>
            <span className="text-xs text-slate-400">
              {expenseBreakdown.length} {isBn ? 'টি ক্যাটাগরি' : 'categories'}
            </span>
          </div>

          {expenseBreakdown.length === 0 ? (
            <div className="py-8 text-center text-xs text-slate-400">
              {isBn
                ? 'এই সময়কালে কোনো খরচের ক্যাটাগরি পাওয়া যায়নি।'
                : 'No expense categories recorded in this period.'}
            </div>
          ) : (
            <div className="space-y-3 max-h-64 overflow-y-auto pr-1">
              {expenseBreakdown.map((item) => (
                <div key={item.categoryId} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700 dark:text-slate-200">
                      {isBn ? item.categoryNameBn : item.categoryName}
                      <span className="text-[10px] text-slate-400 ml-1.5">
                        ({item.count})
                      </span>
                    </span>
                    <span className="font-bold text-slate-900 dark:text-white">
                      {formatBDT(item.amount, { isBengali: isBn })} ({item.sharePercent}%)
                    </span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-rose-500 to-amber-500"
                      style={{ width: `${Math.min(100, Math.max(2, item.sharePercent))}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Balance Sheet & Loans Snapshot */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-4 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-400 block">{t('totalSavings')}</span>
            <span className="text-lg font-bold text-teal-600 dark:text-teal-400">
              {formatBDT(periodSummary.totalSavings, { isBengali: isBn })}
            </span>
          </div>
          <Landmark className="w-7 h-7 text-teal-500/40" />
        </div>

        <div className="p-4 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-400 block">{t('loanGivenOutstanding')}</span>
            <span className="text-lg font-bold text-indigo-600 dark:text-indigo-400">
              {formatBDT(periodSummary.loanGivenOutstanding, { isBengali: isBn })}
            </span>
          </div>
          <Scale className="w-7 h-7 text-indigo-500/40" />
        </div>

        <div className="p-4 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-400 block">{t('totalLiabilities')}</span>
            <span className="text-lg font-bold text-amber-600 dark:text-amber-400">
              {formatBDT(periodSummary.totalLiabilities, { isBengali: isBn })}
            </span>
          </div>
          <Scale className="w-7 h-7 text-amber-500/40" />
        </div>
      </div>

      {/* Transactions in Period Table */}
      <div className="p-5 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-slate-800 dark:text-white">
            {isBn ? 'নির্বাচিত সময়ের লেনদেন সমূহ' : 'Filtered Transactions Ledger'}
          </h3>
          <span className="text-xs text-slate-400">
            {periodTx.length} {isBn ? 'টি লেনদেন' : 'entries'}
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 dark:bg-slate-800/60 border-b border-slate-200 dark:border-slate-800 text-slate-500 font-semibold uppercase">
                <th className="py-2.5 px-3">{t('fieldDate')}</th>
                <th className="py-2.5 px-3">{isBn ? 'ধরন' : 'Type'}</th>
                <th className="py-2.5 px-3">{t('fieldDescription')}</th>
                <th className="py-2.5 px-3">{t('fieldAccount')}</th>
                <th className="py-2.5 px-3 text-right">{isBn ? 'পরিমাণ' : 'Amount'}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
              {periodTx.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-400">
                    {isBn
                      ? 'এই সময়সীমায় কোনো লেনদেন নেই।'
                      : 'No transactions in this date range.'}
                  </td>
                </tr>
              ) : (
                periodTx.map((tx) => (
                  <tr key={tx.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/40">
                    <td className="py-2 px-3 text-slate-500">{formatDhakaDate(tx.occurredAt)}</td>
                    <td className="py-2 px-3 uppercase text-[10px] font-bold">{tx.type}</td>
                    <td className="py-2 px-3 text-slate-900 dark:text-white">{tx.description}</td>
                    <td className="py-2 px-3 text-slate-500">
                      {accounts.find((a) => a.id === tx.accountId)?.name || tx.accountId}
                    </td>
                    <td
                      className={`py-2 px-3 text-right font-bold ${
                        tx.type === 'income'
                          ? 'text-emerald-600'
                          : tx.type === 'expense'
                            ? 'text-rose-600'
                            : 'text-blue-600'
                      }`}
                    >
                      {formatBDT(tx.amount, { isBengali: isBn })}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
      <ExportReportModal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
        ownerName={user?.displayName}
        ownerEmail={user?.email}
        accounts={accounts}
        transactions={transactions}
        categories={categories}
        people={people}
        loans={loans}
        loanRepayments={loanRepayments}
        savingsProducts={savingsProducts}
        savingsGoals={savingsGoals}
        assets={assets}
        liabilities={liabilities}
        onExportSuccess={(files, periodLabel) => {
          showSuccessToast(
            isBn
              ? `${periodLabel} এর সম্পূর্ণ রিপোর্ট এক্সপোর্ট সম্পন্ন হয়েছে: ${files.join(', ')}`
              : `Complete report exported for ${periodLabel}: ${files.join(', ')}`
          );
        }}
      />
    </div>
  );
};
