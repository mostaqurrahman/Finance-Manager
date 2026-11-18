import React, { useState } from 'react';
import { useFinance } from '../context/FinanceContext';
import { useLanguage } from '../context/LanguageContext';
import { Transaction } from '../types/finance';
import { formatBDT, formatDhakaDate, getCurrentDhakaDateString } from '../i18n/formatters';
import { ArrowLeftRight, Plus, X, ArrowRight, Edit2 } from 'lucide-react';

export const TransfersPage: React.FC = () => {
  const { accounts, transactions, createTransaction, updateTransaction } = useFinance();
  const { t } = useLanguage();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTransfer, setEditingTransfer] = useState<Transaction | null>(null);
  const [sourceAccountId, setSourceAccountId] = useState(accounts[0]?.id || '');
  const [destAccountId, setDestAccountId] = useState(accounts[1]?.id || '');
  const [amount, setAmount] = useState('');
  const [transferFee, setTransferFee] = useState('');
  const [date, setDate] = useState(() => getCurrentDhakaDateString());
  const [description, setDescription] = useState('');
  const [note, setNote] = useState('');

  const activeAccounts = accounts.filter((a) => a.status === 'active');
  const transferTransactions = transactions.filter(
    (t) => !t.deletedAt && t.status !== 'cancelled' && t.type === 'transfer'
  );

  const openCreateModal = () => {
    setEditingTransfer(null);
    setSourceAccountId(activeAccounts[0]?.id || '');
    setDestAccountId(activeAccounts[1]?.id || '');
    setAmount('');
    setTransferFee('');
    setDate(getCurrentDhakaDateString());
    setDescription('');
    setNote('');
    setIsModalOpen(true);
  };

  const openEditModal = (tx: Transaction) => {
    setEditingTransfer(tx);
    setSourceAccountId(tx.accountId);
    setDestAccountId(tx.destinationAccountId || '');
    setAmount(tx.amount.toString());
    setTransferFee(tx.transferFee ? tx.transferFee.toString() : '');
    setDate(tx.occurredAt.substring(0, 10));
    setDescription(tx.description || '');
    setNote(tx.note || '');
    setIsModalOpen(true);
  };

  const handleSaveTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    const numAmt = parseFloat(amount);
    if (isNaN(numAmt) || numAmt <= 0) {
      alert(t('invalidAmount'));
      return;
    }

    if (!sourceAccountId || !destAccountId || sourceAccountId === destAccountId) {
      alert('Source and destination accounts must be distinct');
      return;
    }

    const srcAcc = accounts.find((a) => a.id === sourceAccountId);
    const destAcc = accounts.find((a) => a.id === destAccountId);
    const parsedFee = transferFee ? parseFloat(transferFee) : undefined;

    try {
      if (editingTransfer) {
        await updateTransaction({
          ...editingTransfer,
          type: 'transfer',
          amount: Math.round(numAmt),
          accountId: sourceAccountId,
          destinationAccountId: destAccountId,
          paymentMethod: 'Transfer',
          transferFee: parsedFee && parsedFee > 0 ? Math.round(parsedFee) : undefined,
          occurredAt: date,
          description: description.trim() || `Transfer: ${srcAcc?.name} to ${destAcc?.name}`,
          note: note.trim() || undefined,
        });
      } else {
        await createTransaction({
          type: 'transfer',
          amount: Math.round(numAmt),
          accountId: sourceAccountId,
          destinationAccountId: destAccountId,
          paymentMethod: 'Transfer',
          transferFee: parsedFee && parsedFee > 0 ? Math.round(parsedFee) : undefined,
          occurredAt: date,
          timezone: 'Asia/Dhaka',
          description: description.trim() || `Transfer: ${srcAcc?.name} to ${destAcc?.name}`,
          note: note.trim() || undefined,
          status: 'posted',
        });
      }

      setIsModalOpen(false);
      setEditingTransfer(null);
      setAmount('');
      setTransferFee('');
      setDescription('');
      setNote('');
    } catch (err: any) {
      alert(err.message || 'Failed to execute transfer');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">
            {t('navTransfers')}
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Internal movements between your accounts (Does not affect Income or Expense)
          </p>
        </div>

        <button
          onClick={openCreateModal}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md shadow-blue-600/20"
        >
          <Plus className="w-4 h-4" />
          <span>New Transfer</span>
        </button>
      </div>

      {/* Transfer History Table */}
      <div className="bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
          <h2 className="text-sm font-bold text-slate-800 dark:text-white">
            Transfer History ({transferTransactions.length})
          </h2>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 dark:bg-slate-800/60 border-b border-slate-200 dark:border-slate-800 text-slate-500 font-semibold uppercase tracking-wider">
                <th className="py-3 px-4">Date</th>
                <th className="py-3 px-4">From (Source)</th>
                <th className="py-3 px-4">To (Destination)</th>
                <th className="py-3 px-4">Description</th>
                <th className="py-3 px-4 text-right">Transfer Fee</th>
                <th className="py-3 px-4 text-right">Amount</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
              {transferTransactions.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-400">
                    No account transfers recorded yet.
                  </td>
                </tr>
              ) : (
                transferTransactions.map((tx) => {
                  const srcAcc = accounts.find((a) => a.id === tx.accountId);
                  const destAcc = accounts.find((a) => a.id === tx.destinationAccountId);

                  return (
                    <tr key={tx.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/40">
                      <td className="py-3 px-4 text-slate-500 whitespace-nowrap">
                        {formatDhakaDate(tx.occurredAt)}
                      </td>
                      <td className="py-3 px-4 font-bold text-slate-800 dark:text-slate-200">
                        {srcAcc?.name || tx.accountId}
                      </td>
                      <td className="py-3 px-4 font-bold text-slate-800 dark:text-slate-200">
                        {destAcc?.name || tx.destinationAccountId}
                      </td>
                      <td className="py-3 px-4 text-slate-600 dark:text-slate-400 truncate max-w-xs">
                        {tx.description}
                      </td>
                      <td className="py-3 px-4 text-right text-rose-600 font-semibold">
                        {tx.transferFee ? formatBDT(tx.transferFee) : '-'}
                      </td>
                      <td className="py-3 px-4 text-right font-bold text-blue-600 dark:text-blue-400 whitespace-nowrap">
                        {formatBDT(tx.amount)}
                      </td>
                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <button
                          type="button"
                          onClick={() => openEditModal(tx)}
                          title={t('edit')}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-blue-600 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* New Transfer Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="w-full max-w-md bg-white dark:bg-slate-900 rounded-3xl shadow-2xl border border-slate-200 dark:border-slate-800 p-6 space-y-4 my-8">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
              <h3 className="text-base font-bold text-slate-800 dark:text-white flex items-center gap-2">
                <ArrowLeftRight className="w-5 h-5 text-blue-600" />
                <span>{editingTransfer ? `${t('edit')} Transfer` : 'Transfer Money'}</span>
              </h3>
              <button onClick={() => setIsModalOpen(false)} className="p-1 text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveTransfer} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  {t('fieldAmount')} (৳) *
                </label>
                <input
                  type="number"
                  min="1"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0"
                  className="w-full px-3 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-lg font-bold"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1">
                    {t('fieldSourceAccount')} *
                  </label>
                  <select
                    required
                    value={sourceAccountId}
                    onChange={(e) => setSourceAccountId(e.target.value)}
                    className="w-full px-2.5 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs"
                  >
                    {activeAccounts.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1">
                    {t('fieldDestinationAccount')} *
                  </label>
                  <select
                    required
                    value={destAccountId}
                    onChange={(e) => setDestAccountId(e.target.value)}
                    className="w-full px-2.5 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs"
                  >
                    {activeAccounts
                      .filter((a) => a.id !== sourceAccountId)
                      .map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1">
                    {t('fieldTransferFee')} (৳)
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={transferFee}
                    onChange={(e) => setTransferFee(e.target.value)}
                    placeholder="0"
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1">
                    {t('fieldDate')}
                  </label>
                  <input
                    type="date"
                    required
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  {t('fieldDescription')}
                </label>
                <input
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional description"
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  Note
                </label>
                <input
                  type="text"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Optional note"
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-xs"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100 dark:border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-xl border border-slate-300 dark:border-slate-700 text-xs font-semibold"
                >
                  {t('cancel')}
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md"
                >
                  {editingTransfer ? t('save') : 'Confirm Transfer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
