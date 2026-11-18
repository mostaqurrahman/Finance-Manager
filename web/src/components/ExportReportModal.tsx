import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  Calendar,
  CheckCircle2,
  Download,
  FileCheck2,
  FileSpreadsheet,
  FileText,
  Layers,
  Loader2,
  X,
} from 'lucide-react';
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
} from '../types/finance';
import { useLanguage } from '../context/LanguageContext';
import {
  ReportExportFormat,
  ReportPeriodType,
  buildUnifiedFinancialReport,
  getAvailableReportPeriods,
  getUnifiedReportFileName,
} from '../services/unifiedReportEngine';
import { exportCompleteReport } from '../services/exportService';

interface ExportReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  ownerName?: string | null;
  ownerEmail?: string | null;
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  people: Person[];
  loans: Loan[];
  loanRepayments: LoanRepayment[];
  savingsProducts: SavingsProduct[];
  savingsGoals: SavingsGoal[];
  assets: Asset[];
  liabilities: Liability[];
  onExportSuccess: (files: string[], periodLabel: string) => void;
}

export const ExportReportModal: React.FC<ExportReportModalProps> = ({
  isOpen,
  onClose,
  ownerName,
  ownerEmail,
  accounts,
  transactions,
  categories,
  people,
  loans,
  loanRepayments,
  savingsProducts,
  savingsGoals,
  assets,
  liabilities,
  onExportSuccess,
}) => {
  const { t, language } = useLanguage();
  const isBn = language === 'bn';

  const [periodType, setPeriodType] = useState<ReportPeriodType>('monthly');
  const [selectedPeriodKey, setSelectedPeriodKey] = useState<string>('');
  const [format, setFormat] = useState<ReportExportFormat>('both');
  const [isExporting, setIsExporting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const availablePeriods = useMemo(
    () => getAvailableReportPeriods(transactions, periodType),
    [transactions, periodType]
  );

  useEffect(() => {
    if (!isOpen) return;
    setErrorMsg(null);
    if (availablePeriods.length === 0) {
      setSelectedPeriodKey('');
    } else if (!availablePeriods.some((p) => p.key === selectedPeriodKey)) {
      setSelectedPeriodKey(availablePeriods[0].key);
    }
  }, [isOpen, availablePeriods, selectedPeriodKey]);

  const selectedPeriod = useMemo(
    () => availablePeriods.find((p) => p.key === selectedPeriodKey) || null,
    [availablePeriods, selectedPeriodKey]
  );

  if (!isOpen) return null;

  const formatLabel =
    format === 'pdf'
      ? t('formatPdfOnly')
      : format === 'xlsx'
        ? t('formatExcelOnly')
        : t('formatBoth');

  const expectedFileNames = selectedPeriod
    ? format === 'both'
      ? [
          getUnifiedReportFileName(periodType, selectedPeriod.key, 'pdf'),
          getUnifiedReportFileName(periodType, selectedPeriod.key, 'xlsx'),
        ]
      : [getUnifiedReportFileName(periodType, selectedPeriod.key, format)]
    : [];

  const handleExportComplete = async () => {
    if (!selectedPeriod) {
      setErrorMsg(
        isBn
          ? 'অনুগ্রহ করে একটি বৈধ সময়কাল (মাস বা বছর) নির্বাচন করুন।'
          : 'Please select an available reporting period first.'
      );
      return;
    }

    setErrorMsg(null);
    setIsExporting(true);
    try {
      const report = buildUnifiedFinancialReport({
        periodType,
        periodKey: selectedPeriod.key,
        ownerName,
        ownerEmail,
        accounts,
        transactions,
        categories,
        people,
        loans,
        loanRepayments,
        savingsProducts,
        savingsGoals,
        assets,
        liabilities,
      });

      const downloadedFiles = await exportCompleteReport(report, format);
      const label = isBn ? selectedPeriod.labelBn : selectedPeriod.labelEn;
      onExportSuccess(downloadedFiles, label);
      onClose();
    } catch (err: any) {
      setErrorMsg(
        err?.message ||
          (isBn
            ? 'রিপোর্ট তৈরি করতে সমস্যা হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।'
            : 'Failed to export report. Please try again.')
      );
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-950/60 backdrop-blur-sm p-0 sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="export-report-modal-title"
      data-testid="export-report-modal"
    >
      <div className="w-full sm:max-w-lg max-h-[92vh] flex flex-col rounded-t-3xl sm:rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xl overflow-hidden">
        {/* Sticky Header */}
        <div className="px-5 py-4 border-b border-slate-100 dark:border-slate-800 flex items-start justify-between gap-3 bg-white dark:bg-slate-900">
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-2xl bg-emerald-100 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 flex items-center justify-center flex-shrink-0">
              <FileCheck2 className="w-5 h-5" />
            </div>
            <div>
              <h2
                id="export-report-modal-title"
                className="text-base font-bold text-slate-900 dark:text-white"
              >
                {t('exportReportTitle')}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 leading-relaxed">
                {t('exportReportSubtitle')}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isExporting}
            aria-label={t('close')}
            className="p-2 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 dark:hover:text-slate-200 transition-colors cursor-pointer min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="p-5 space-y-5 overflow-y-auto">
          {/* 1. Report Period Type */}
          <div className="space-y-2.5">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-300 block">
              {t('exportPeriodTypeLabel')}
            </label>
            <div className="grid grid-cols-2 gap-2.5">
              {(
                [
                  { id: 'monthly', label: t('monthlyReport') },
                  { id: 'yearly', label: t('yearlyReport') },
                ] as const
              ).map((opt) => {
                const active = periodType === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    disabled={isExporting}
                    onClick={() => setPeriodType(opt.id)}
                    data-testid={`period-type-${opt.id}`}
                    className={`min-h-[48px] px-3 py-2.5 rounded-2xl border text-xs font-bold flex items-center justify-center gap-2 transition-all cursor-pointer ${
                      active
                        ? 'bg-emerald-600 border-emerald-600 text-white shadow-md shadow-emerald-600/20'
                        : 'bg-slate-50 dark:bg-slate-800/70 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:border-emerald-400'
                    }`}
                  >
                    <Calendar className="w-4 h-4 flex-shrink-0" />
                    <span>{opt.label}</span>
                  </button>
                );
              })}
            </div>

            {/* Dynamic Non-Empty Period Selector */}
            <div className="pt-1">
              <label
                htmlFor="unified-period-select"
                className="text-xs font-semibold text-slate-600 dark:text-slate-300 block mb-1.5"
              >
                {periodType === 'monthly' ? t('selectMonthLabel') : t('selectYearLabel')}
              </label>

              {availablePeriods.length === 0 ? (
                <div className="p-3 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-xs text-amber-800 dark:text-amber-200 flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{t('exportNoPeriodsAvailable')}</span>
                </div>
              ) : (
                <select
                  id="unified-period-select"
                  value={selectedPeriodKey}
                  disabled={isExporting}
                  onChange={(e) => setSelectedPeriodKey(e.target.value)}
                  data-testid="export-period-select"
                  className="w-full min-h-[48px] px-3.5 py-2.5 rounded-2xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-sm font-semibold text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                >
                  {availablePeriods.map((period) => (
                    <option key={period.key} value={period.key}>
                      {isBn
                        ? `${period.labelBn} (${period.txCount}টি লেনদেন)`
                        : `${period.labelEn} (${period.txCount} transactions)`}
                    </option>
                  ))}
                </select>
              )}
            </div>
          </div>

          {/* 2. Export Format */}
          <div className="space-y-2.5">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-600 dark:text-slate-300 block">
              {t('exportFormatLabel')}
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              {(
                [
                  { id: 'pdf', label: t('formatPdfOnly'), icon: FileText },
                  { id: 'xlsx', label: t('formatExcelOnly'), icon: FileSpreadsheet },
                  { id: 'both', label: t('formatBoth'), icon: Layers },
                ] as const
              ).map((fmt) => {
                const Icon = fmt.icon;
                const active = format === fmt.id;
                return (
                  <button
                    key={fmt.id}
                    type="button"
                    disabled={isExporting}
                    onClick={() => setFormat(fmt.id)}
                    data-testid={`export-format-${fmt.id}`}
                    className={`min-h-[52px] px-3 py-2.5 rounded-2xl border text-xs font-bold flex sm:flex-col items-center justify-center gap-1.5 transition-all cursor-pointer ${
                      active
                        ? 'bg-emerald-50 dark:bg-emerald-950/60 border-emerald-600 text-emerald-700 dark:text-emerald-300 ring-1 ring-emerald-600'
                        : 'bg-slate-50 dark:bg-slate-800/70 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:border-emerald-400'
                    }`}
                  >
                    <Icon className="w-4 h-4 flex-shrink-0" />
                    <span className="text-center leading-tight">{fmt.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 3. Selection Summary Box */}
          <div
            className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/80 space-y-2 text-xs"
            data-testid="export-selection-summary"
          >
            <div className="flex items-center gap-1.5 font-bold text-slate-800 dark:text-white">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <span>{t('exportSelectionSummary')}</span>
            </div>

            <div className="grid grid-cols-2 gap-y-1.5 gap-x-3 pt-1">
              <span className="text-slate-500 dark:text-slate-400">
                {isBn ? 'রিপোর্টের ধরন:' : 'Report Type:'}
              </span>
              <span className="font-semibold text-slate-900 dark:text-white text-right">
                {periodType === 'monthly' ? t('monthlyReport') : t('yearlyReport')}
              </span>

              <span className="text-slate-500 dark:text-slate-400">
                {isBn ? 'নির্বাচিত সময়কাল:' : 'Selected Period:'}
              </span>
              <span className="font-semibold text-slate-900 dark:text-white text-right">
                {selectedPeriod
                  ? isBn
                    ? selectedPeriod.labelBn
                    : selectedPeriod.labelEn
                  : '-'}
              </span>

              <span className="text-slate-500 dark:text-slate-400">
                {isBn ? 'ফাইল ফরম্যাট:' : 'Format:'}
              </span>
              <span className="font-semibold text-slate-900 dark:text-white text-right">
                {formatLabel}
              </span>

              <span className="text-slate-500 dark:text-slate-400">
                {t('exportTransactionsIncluded')}:
              </span>
              <span className="font-bold text-emerald-700 dark:text-emerald-400 text-right">
                {selectedPeriod ? selectedPeriod.txCount : 0}
              </span>
            </div>

            {expectedFileNames.length > 0 && (
              <div className="pt-2 border-t border-slate-200 dark:border-slate-700/80 text-[11px] text-slate-500 dark:text-slate-400">
                <span className="font-semibold">
                  {isBn ? 'ডাউনলোড ফাইল: ' : 'Output file(s): '}
                </span>
                <span className="font-mono text-slate-700 dark:text-slate-200 break-all">
                  {expectedFileNames.join(' , ')}
                </span>
              </div>
            )}

            <p className="text-[11px] text-slate-500 dark:text-slate-400 pt-1">
              {isBn
                ? `সকল ${accounts.length}টি অ্যাকাউন্টের বিস্তারিত স্টেটমেন্ট, ব্যক্তি, ক্যাটাগরি, ঋণ, সঞ্চয়, সম্পদ ও দায় স্বয়ংক্রিয়ভাবে যুক্ত হবে।`
                : `Automatically includes all ${accounts.length} account statements, person, category, loan, savings, asset, and liability reports.`}
            </p>
          </div>

          {/* Error Feedback */}
          {errorMsg && (
            <div className="p-3 rounded-2xl bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-800 text-xs text-rose-700 dark:text-rose-200 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}
        </div>

        {/* Sticky Footer Action */}
        <div className="px-5 py-4 border-t border-slate-100 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/95 flex items-center justify-end gap-2.5">
          <button
            type="button"
            onClick={onClose}
            disabled={isExporting}
            className="min-h-[48px] px-4 py-2.5 rounded-2xl border border-slate-200 dark:border-slate-700 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
          >
            {t('cancel')}
          </button>

          <button
            type="button"
            onClick={handleExportComplete}
            disabled={isExporting || !selectedPeriod || selectedPeriod.txCount === 0}
            data-testid="export-complete-report-btn"
            className="flex-1 sm:flex-none min-h-[48px] px-5 py-2.5 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-xs font-bold shadow-lg shadow-emerald-600/25 flex items-center justify-center gap-2 transition-all cursor-pointer"
          >
            {isExporting ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Download className="w-4 h-4" />
            )}
            <span>
              {isExporting ? t('exportGeneratingBtn') : t('exportCompleteBtn')}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
};
