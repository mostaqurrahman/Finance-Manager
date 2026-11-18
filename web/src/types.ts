export interface UserProfile {
  id?: string;
  userId: string;
  displayName: string;
  email?: string;
  currency: string;
  language: string;
  setupCompleted: boolean;
  createdAt?: any;
  updatedAt?: any;
}

export interface Account {
  id: string;
  userId: string;
  name: string;
  type: 'CASH' | 'BANK' | 'MOBILE_BANKING' | 'SAVINGS' | 'DPS_FDR' | 'OTHER';
  provider?: string;
  accountNumber?: string;
  openingBalance: number;
  currentBalance: number;
  currency: string;
  isArchived?: boolean;
}

export interface Transaction {
  id: string;
  userId: string;
  type: 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'LOAN_GIVEN' | 'LOAN_RECEIVED' | 'LOAN_REPAYMENT_RECEIVED' | 'LOAN_REPAYMENT_PAID' | 'SAVINGS_DEPOSIT' | 'SAVINGS_WITHDRAWAL' | 'REFUND' | 'ADJUSTMENT';
  amount: number;
  accountId: string;
  accountName?: string;
  toAccountId?: string;
  toAccountName?: string;
  categoryId?: string;
  categoryName?: string;
  personId?: string;
  personName?: string;
  date: string;
  time?: string;
  description?: string;
  linkedExpenseId?: string;
  isSplit?: boolean;
  createdAt?: any;
}

export interface Loan {
  id: string;
  userId: string;
  type: 'GIVEN' | 'TAKEN';
  partyName: string;
  principal: number;
  paidAmount: number;
  remainingAmount: number;
  date: string;
  dueDate?: string;
  status: 'PENDING' | 'PARTIAL' | 'COMPLETED' | 'OVERDUE';
  notes?: string;
}

export interface Budget {
  id: string;
  userId: string;
  categoryId: string;
  categoryName: string;
  month: string;
  limitAmount: number;
  spentAmount?: number;
  alertThreshold: number;
}

export interface SavingsGoal {
  id: string;
  userId: string;
  title: string;
  targetAmount: number;
  currentAmount: number;
  targetDate?: string;
  category?: string;
  status: string;
}

export interface Category {
  id: string;
  userId: string;
  name: string;
  nameBn?: string;
  type: 'INCOME' | 'EXPENSE';
}

export interface Person {
  id: string;
  userId: string;
  name: string;
  relationship?: string;
}
