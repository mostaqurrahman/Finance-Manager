import {
  extractBengaliAmount,
  normalizeBengaliDigits,
  parseBengaliFinanceText,
} from '../services/bengaliVoiceParser';

function assertEqual<T>(actual: T, expected: T, label: string) {
  if (actual !== expected) {
    throw new Error(`${label}: expected ${String(expected)}, got ${String(actual)}`);
  }
}

const context = {
  todayDateStr: '2026-11-14',
  accounts: [
    { id: 'acc_cash', name: 'Personal Cash', provider: 'Cash' },
    { id: 'acc_bkash', name: 'bKash Personal', provider: 'bKash' },
    { id: 'acc_dbbl', name: 'DBBL Savings', provider: 'DBBL' },
  ],
  categories: [
    { id: 'cat_food', name: 'Food & Grocery', nameBn: 'খাবার ও বাজার', type: 'expense' },
    { id: 'cat_family', name: 'Family Expense', nameBn: 'পরিবার খরচ', type: 'expense' },
    { id: 'cat_salary', name: 'Salary', nameBn: 'বেতন', type: 'income' },
  ],
  people: [
    { id: 'p_shakil', name: 'Shakil', nameBn: 'শাকিল' },
    { id: 'p_wife', name: 'Wife', nameBn: 'স্ত্রী' },
  ],
};

// 1. Bengali digits normalization
assertEqual(normalizeBengaliDigits('১২,৫০০ টাকা'), '12,500 টাকা', 'Normalize Bengali digits');

// 2. Spoken Bengali word amounts
assertEqual(extractBengaliAmount('পাঁচ হাজার টাকা'), 5000, 'Word amount: পাঁচ হাজার');
assertEqual(extractBengaliAmount('বারো শত টাকা বাজার'), 1200, 'Word amount: বারো শত');
assertEqual(extractBengaliAmount('দেড় লাখ টাকা'), 150000, 'Word amount: দেড় লাখ');

// 3. Grocery expense with bKash and yesterday date
const grocery = parseBengaliFinanceText(
  'গতকাল বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ করেছি',
  context
);
assertEqual(grocery.type, 'expense', 'Grocery type');
assertEqual(grocery.amount, 1200, 'Grocery amount');
assertEqual(grocery.accountName, 'bKash Personal', 'Grocery account match');
assertEqual(grocery.categoryName, 'Food & Grocery', 'Grocery category match');
assertEqual(grocery.date, '2026-11-13', 'Yesterday date resolved');

// 4. Income with DBBL bank & comma-formatted Bengali numerals
const salary = parseBengaliFinanceText(
  'অফিস থেকে বেতন পেয়েছি ৬০,০০০ টাকা DBBL একাউন্টে',
  context
);
assertEqual(salary.type, 'income', 'Salary type');
assertEqual(salary.amount, 60000, 'Salary amount');
assertEqual(salary.accountName, 'DBBL Savings', 'Salary bank match');
assertEqual(salary.categoryName, 'Salary', 'Salary category match');

// 5. Loan given to friend
const loan = parseBengaliFinanceText(
  'বন্ধু শাকিলকে ক্যাশ থেকে ৩০০০ টাকা ধার দিয়েছি',
  context
);
assertEqual(loan.type, 'loan_given', 'Loan given type');
assertEqual(loan.amount, 3000, 'Loan given amount');
assertEqual(loan.accountName, 'Personal Cash', 'Loan account match');
assertEqual(loan.personName, 'Shakil', 'Loan person match');

console.log('✓ All bengaliVoiceParser tests passed (5/5)');
