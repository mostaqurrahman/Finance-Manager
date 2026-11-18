import { Category, Transaction } from '../types/finance';

export interface SpendingPatternInsight {
  title: string;
  detail: string;
  trend: 'up' | 'down' | 'stable';
}

export interface SavingsCategoryOpportunity {
  categoryName: string;
  currentSpend: number;
  estimatedMonthlySavings: number;
  actionableTip: string;
  priority: 'high' | 'medium' | 'low';
}

export interface AiSpendingInsightsResponse {
  headline: string;
  spendingSummary: string;
  patterns: SpendingPatternInsight[];
  savingsOpportunities: SavingsCategoryOpportunity[];
  savingsPotentialTotal: number;
  generatedAt: string;
  model: string;
}

export interface CategorySpendDigestRow {
  categoryId: string;
  categoryName: string;
  amount: number;
  txCount: number;
  sharePercent: number;
}

export interface InsightsDigestPayload {
  periodLabel: string;
  language: 'bn' | 'en';
  totalIncome: number;
  totalExpense: number;
  netCashFlow: number;
  savingsRatePercent: number;
  txCount: number;
  categoryBreakdown: CategorySpendDigestRow[];
  topExpenses: Array<{
    date: string;
    category: string;
    amount: number;
    description: string;
  }>;
  budgetStatuses: Array<{
    categoryName: string;
    limitAmount: number;
    spentAmount: number;
    usagePercent: number;
  }>;
}

/**
 * Computes a clean, accounting-accurate spending digest for Gemini analysis:
 * - Excludes deleted/cancelled transactions and transfers from spending
 * - Deducts linked refunds from gross category expenses
 * - Attributes split lines accurately
 */
export function prepareInsightsDigest(params: {
  transactions: Transaction[];
  categories: Category[];
  periodLabel: string;
  language: 'bn' | 'en';
  budgets?: Array<{ categoryId: string; amount: number }>;
}): InsightsDigestPayload {
  const { transactions, categories, periodLabel, language, budgets = [] } = params;
  const activeTx = transactions.filter((t) => !t.deletedAt && t.status !== 'cancelled');
  const categoryMap = new Map(categories.map((c) => [c.id, c]));

  let totalIncome = 0;
  let grossExpense = 0;
  let totalRefunds = 0;

  const refundsByTxId = new Map<string, number>();
  for (const tx of activeTx) {
    if (tx.type === 'refund') {
      totalRefunds += tx.amount || 0;
      if (tx.linkedTransactionId) {
        refundsByTxId.set(
          tx.linkedTransactionId,
          (refundsByTxId.get(tx.linkedTransactionId) || 0) + (tx.amount || 0)
        );
      }
    }
  }

  const catBuckets = new Map<string, { name: string; amount: number; txCount: number }>();
  const expenseItems: Array<{ date: string; category: string; amount: number; description: string }> = [];

  for (const tx of activeTx) {
    if (tx.type === 'income') {
      totalIncome += tx.amount || 0;
      continue;
    }
    if (tx.type !== 'expense') continue;

    grossExpense += tx.amount || 0;
    const refunded = refundsByTxId.get(tx.id) ?? tx.refundedAmount ?? 0;
    const effectiveExpense = Math.max(0, (tx.amount || 0) - refunded);

    if (tx.splitLines && tx.splitLines.length > 0) {
      const rawTotal = tx.amount > 0 ? tx.amount : 1;
      const ratio = effectiveExpense / rawTotal;
      for (const split of tx.splitLines) {
        const cid = split.categoryId || 'uncategorized';
        const cat = categoryMap.get(cid);
        const catName = cat ? (language === 'bn' && cat.nameBn ? cat.nameBn : cat.name) : 'Other';
        const splitAmt = Math.round((split.amount || 0) * ratio);
        const prev = catBuckets.get(cid) || { name: catName, amount: 0, txCount: 0 };
        prev.amount += splitAmt;
        prev.txCount += 1;
        catBuckets.set(cid, prev);
      }
    } else {
      const cid = tx.categoryId || 'uncategorized';
      const cat = categoryMap.get(cid);
      const catName = cat ? (language === 'bn' && cat.nameBn ? cat.nameBn : cat.name) : 'Other';
      const prev = catBuckets.get(cid) || { name: catName, amount: 0, txCount: 0 };
      prev.amount += effectiveExpense;
      prev.txCount += 1;
      catBuckets.set(cid, prev);

      if (effectiveExpense > 0) {
        expenseItems.push({
          date: (tx.occurredAt || '').substring(0, 10),
          category: catName,
          amount: Math.round(effectiveExpense),
          description: tx.description || catName,
        });
      }
    }
  }

  const totalExpense = Math.max(0, Math.round(grossExpense - totalRefunds));
  const netCashFlow = Math.round(totalIncome - totalExpense);
  const savingsRatePercent =
    totalIncome > 0 ? Math.round((netCashFlow / totalIncome) * 1000) / 10 : 0;

  const categoryBreakdown: CategorySpendDigestRow[] = Array.from(catBuckets.entries())
    .map(([categoryId, data]) => ({
      categoryId,
      categoryName: data.name,
      amount: Math.round(data.amount),
      txCount: data.txCount,
      sharePercent: totalExpense > 0 ? Math.round((data.amount / totalExpense) * 1000) / 10 : 0,
    }))
    .filter((c) => c.amount > 0)
    .sort((a, b) => b.amount - a.amount);

  const topExpenses = expenseItems
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 12);

  const budgetStatuses = budgets
    .map((b) => {
      const cat = categoryMap.get(b.categoryId);
      const catName = cat ? (language === 'bn' && cat.nameBn ? cat.nameBn : cat.name) : b.categoryId;
      const spent = catBuckets.get(b.categoryId)?.amount || 0;
      const usagePercent = b.amount > 0 ? Math.round((spent / b.amount) * 100) : 0;
      return {
        categoryName: catName,
        limitAmount: b.amount,
        spentAmount: Math.round(spent),
        usagePercent,
      };
    })
    .filter((b) => b.limitAmount > 0);

  return {
    periodLabel,
    language,
    totalIncome: Math.round(totalIncome),
    totalExpense,
    netCashFlow,
    savingsRatePercent,
    txCount: activeTx.length,
    categoryBreakdown,
    topExpenses,
    budgetStatuses,
  };
}

export async function fetchAiSpendingInsights(
  digest: InsightsDigestPayload
): Promise<AiSpendingInsightsResponse> {
  const res = await fetch('/api/ai-insights', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(digest),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data?.error || `AI insights request failed (${res.status})`);
  }

  return data as AiSpendingInsightsResponse;
}
