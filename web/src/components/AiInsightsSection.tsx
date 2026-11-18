import React, { useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowDownRight,
  ArrowUpRight,
  Minus,
  PiggyBank,
  RefreshCw,
  Sparkles,
  TrendingDown,
} from 'lucide-react';
import { Budget, Category, Transaction } from '../types/finance';
import { useLanguage } from '../context/LanguageContext';
import { formatBDT } from '../i18n/formatters';
import { DateFilterRange, filterTransactionsByDate } from '../services/ledgerEngine';
import {
  AiSpendingInsightsResponse,
  fetchAiSpendingInsights,
  prepareInsightsDigest,
} from '../services/aiInsightsService';

interface AiInsightsSectionProps {
  transactions: Transaction[];
  categories: Category[];
  budgets: Budget[];
  dateRange: DateFilterRange;
}

export const AiInsightsSection: React.FC<AiInsightsSectionProps> = ({
  transactions,
  categories,
  budgets,
  dateRange,
}) => {
  const { t, language } = useLanguage();
  const isBn = language === 'bn';

  const [insights, setInsights] = useState<AiSpendingInsightsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const periodTransactions = useMemo(
    () => filterTransactionsByDate(transactions, dateRange),
    [transactions, dateRange]
  );

  const periodLabel = useMemo(() => {
    if (dateRange.startDate && dateRange.endDate) {
      return `${dateRange.startDate} to ${dateRange.endDate}`;
    }
    return isBn ? 'সকল সময়' : 'All Time';
  }, [dateRange, isBn]);

  const digest = useMemo(
    () =>
      prepareInsightsDigest({
        transactions: periodTransactions,
        categories,
        budgets,
        periodLabel,
        language,
      }),
    [periodTransactions, categories, budgets, periodLabel, language]
  );

  const hasActivity = digest.totalIncome > 0 || digest.totalExpense > 0;

  const handleAnalyze = async () => {
    if (!hasActivity || isLoading) return;
    setIsLoading(true);
    setErrorMsg(null);
    try {
      const result = await fetchAiSpendingInsights(digest);
      setInsights(result);
    } catch (err: any) {
      setErrorMsg(
        err?.message ||
          (isBn
            ? 'Gemini AI বিশ্লেষণ লোড করতে সমস্যা হয়েছে।'
            : 'Unable to load Gemini AI spending insights.')
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <section
      data-testid="ai-insights-section"
      className="p-5 rounded-3xl bg-gradient-to-br from-emerald-50/90 via-white to-teal-50/70 dark:from-slate-900 dark:via-slate-900 dark:to-emerald-950/30 border border-emerald-200/80 dark:border-emerald-800/50 shadow-sm space-y-4"
    >
      {/* Header Row */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-emerald-600 to-teal-600 text-white flex items-center justify-center shadow-md shadow-emerald-600/20 flex-shrink-0">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="text-sm sm:text-base font-bold text-slate-900 dark:text-white">
                {t('aiInsightsTitle')}
              </h3>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-700 dark:bg-emerald-900/60 dark:text-emerald-300">
                Gemini AI
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              {t('aiInsightsSubtitle')}
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={handleAnalyze}
          disabled={!hasActivity || isLoading}
          data-testid="generate-ai-insights-btn"
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 min-h-[44px] rounded-2xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-xs font-bold shadow-md shadow-emerald-600/20 transition-all cursor-pointer flex-shrink-0"
        >
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          <span>
            {isLoading
              ? t('aiInsightsAnalyzing')
              : insights
                ? t('aiInsightsRefreshBtn')
                : t('aiInsightsAnalyzeBtn')}
          </span>
        </button>
      </div>

      {/* Quick Top Categories Strip */}
      {hasActivity && digest.categoryBreakdown.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-[11px]">
          <span className="font-semibold text-slate-500 dark:text-slate-400">
            {isBn ? 'শীর্ষ ব্যয়ের খাত:' : 'Top spend:'}
          </span>
          {digest.categoryBreakdown.slice(0, 4).map((cat) => (
            <span
              key={cat.categoryId}
              className="px-2.5 py-1 rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 font-medium"
            >
              {cat.categoryName}: {formatBDT(cat.amount, { isBengali: isBn })} ({cat.sharePercent}%)
            </span>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!hasActivity && (
        <div className="p-4 rounded-2xl bg-white/80 dark:bg-slate-800/50 border border-slate-200/80 dark:border-slate-700/60 text-xs text-slate-500 dark:text-slate-400">
          {t('aiInsightsNoData')}
        </div>
      )}

      {/* Idle Prompt */}
      {hasActivity && !insights && !isLoading && !errorMsg && (
        <div className="p-4 rounded-2xl bg-white/80 dark:bg-slate-800/50 border border-dashed border-emerald-300 dark:border-emerald-800 text-xs text-slate-600 dark:text-slate-300">
          {t('aiInsightsEmptyPrompt')}
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div
          data-testid="ai-insights-loading"
          className="p-5 rounded-2xl bg-white/80 dark:bg-slate-800/60 border border-emerald-200 dark:border-emerald-800 flex items-center gap-3 text-xs font-medium text-emerald-700 dark:text-emerald-300"
        >
          <Sparkles className="w-4 h-4 animate-pulse flex-shrink-0" />
          <span>{t('aiInsightsAnalyzing')}</span>
        </div>
      )}

      {/* Error State (Transparent Error Propagation) */}
      {errorMsg && (
        <div
          data-testid="ai-insights-error"
          className="p-4 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 text-xs text-rose-700 dark:text-rose-200 flex items-start gap-2.5"
        >
          <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Generated AI Insights Content */}
      {insights && !isLoading && (
        <div data-testid="ai-insights-results" className="space-y-4">
          {/* Headline & Summary Banner */}
          <div className="p-4 rounded-2xl bg-white dark:bg-slate-900/90 border border-emerald-100 dark:border-emerald-900/50 space-y-1.5">
            {insights.headline && (
              <p className="text-sm font-bold text-emerald-700 dark:text-emerald-300">
                {insights.headline}
              </p>
            )}
            <p className="text-xs text-slate-700 dark:text-slate-200 leading-relaxed">
              {insights.spendingSummary}
            </p>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Key Spending Patterns */}
            <div className="p-4 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200/80 dark:border-slate-800 space-y-3">
              <div className="flex items-center gap-2">
                <TrendingDown className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                  {t('aiInsightsSpendingPatterns')}
                </h4>
              </div>

              <div className="space-y-2.5">
                {insights.patterns.map((pattern, idx) => (
                  <div
                    key={`${pattern.title}-${idx}`}
                    className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800/70 flex items-start gap-2.5"
                  >
                    <div
                      className={`w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5 ${
                        pattern.trend === 'up'
                          ? 'bg-rose-100 text-rose-600 dark:bg-rose-950/60 dark:text-rose-400'
                          : pattern.trend === 'down'
                            ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-400'
                            : 'bg-blue-100 text-blue-600 dark:bg-blue-950/60 dark:text-blue-400'
                      }`}
                    >
                      {pattern.trend === 'up' ? (
                        <ArrowUpRight className="w-3.5 h-3.5" />
                      ) : pattern.trend === 'down' ? (
                        <ArrowDownRight className="w-3.5 h-3.5" />
                      ) : (
                        <Minus className="w-3.5 h-3.5" />
                      )}
                    </div>
                    <div className="space-y-0.5 min-w-0">
                      <p className="text-xs font-bold text-slate-900 dark:text-white">
                        {pattern.title}
                      </p>
                      <p className="text-[11px] text-slate-600 dark:text-slate-300 leading-relaxed">
                        {pattern.detail}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Potential Savings Categories */}
            <div className="p-4 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200/80 dark:border-slate-800 space-y-3">
              <div className="flex items-center justify-between gap-2 flex-wrap">
                <div className="flex items-center gap-2">
                  <PiggyBank className="w-4 h-4 text-teal-600 dark:text-teal-400" />
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                    {t('aiInsightsSavingsCategories')}
                  </h4>
                </div>

                {insights.savingsPotentialTotal > 0 && (
                  <span className="px-2.5 py-1 rounded-full text-[11px] font-bold bg-teal-100 text-teal-800 dark:bg-teal-950/70 dark:text-teal-300">
                    {t('aiInsightsEstSavings')}:{' '}
                    {formatBDT(insights.savingsPotentialTotal, { isBengali: isBn })}
                  </span>
                )}
              </div>

              <div className="space-y-2.5">
                {insights.savingsOpportunities.map((opp, idx) => (
                  <div
                    key={`${opp.categoryName}-${idx}`}
                    className="p-3 rounded-xl bg-teal-50/60 dark:bg-teal-950/20 border border-teal-100 dark:border-teal-900/40 space-y-1.5"
                  >
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                      <span className="text-xs font-bold text-slate-900 dark:text-white">
                        {opp.categoryName}
                      </span>
                      <div className="flex items-center gap-1.5">
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-600 text-white">
                          +{formatBDT(opp.estimatedMonthlySavings, { isBengali: isBn })}
                        </span>
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                            opp.priority === 'high'
                              ? 'bg-rose-100 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300'
                              : opp.priority === 'low'
                                ? 'bg-slate-200 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
                                : 'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300'
                          }`}
                        >
                          {opp.priority}
                        </span>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-600 dark:text-slate-300 leading-relaxed">
                      {opp.actionableTip}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
