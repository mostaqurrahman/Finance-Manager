/**
 * Deterministic Bengali / Banglish / English Personal Finance Voice & Text Parser
 * Used on both Web client (instant offline/local parsing) and server fallback when Gemini API key is absent.
 */

export interface ParserContextAccount {
  id: string;
  name: string;
  provider?: string;
}

export interface ParserContextCategory {
  id: string;
  name: string;
  nameBn?: string;
  type?: string;
}

export interface ParserContextPerson {
  id: string;
  name: string;
  nameBn?: string;
}

export interface VoiceParseContext {
  accounts?: ParserContextAccount[];
  categories?: ParserContextCategory[];
  people?: ParserContextPerson[];
  todayDateStr?: string;
}

export interface ParsedFinanceDraft {
  transcript: string;
  type:
    | 'expense'
    | 'income'
    | 'transfer'
    | 'loan_given'
    | 'loan_received'
    | 'loan_repayment_received'
    | 'loan_repayment_paid'
    | 'savings_deposit'
    | 'savings_withdrawal'
    | 'refund';
  amount: number;
  categoryName: string;
  personName: string;
  paymentMethod: string;
  accountName: string;
  description: string;
  note: string;
  date: string;
  confidence: number;
  source: 'gemini' | 'local-parser';
}

const BENGALI_DIGIT_MAP: Record<string, string> = {
  '০': '0',
  '১': '1',
  '২': '2',
  '৩': '3',
  '৪': '4',
  '৫': '5',
  '৬': '6',
  '৭': '7',
  '৮': '8',
  '৯': '9',
};

const BENGALI_WORD_NUMBERS: Record<string, number> = {
  এক: 1,
  দুই: 2,
  তিন: 3,
  চার: 4,
  পাঁচ: 5,
  পান্চ: 5,
  ছয়: 6,
  ছয়: 6,
  সাত: 7,
  আট: 8,
  নয়: 9,
  নয়: 9,
  দশ: 10,
  এগারো: 11,
  বারো: 12,
  তেরো: 13,
  চৌদ্দ: 14,
  পনেরো: 15,
  ষোল: 16,
  সতেরো: 17,
  আঠারো: 18,
  উনিশ: 19,
  বিশ: 20,
  পঁচিশ: 25,
  ত্রিশ: 30,
  চল্লিশ: 40,
  পঞ্চাশ: 50,
  ষাট: 60,
  সত্তর: 70,
  আশি: 80,
  নব্বই: 90,
  একশ: 100,
  একশো: 100,
  পাঁচশ: 500,
  পাঁচশো: 500,
  দেড়: 1.5,
  দেড়: 1.5,
  আড়াই: 2.5,
  আড়াই: 2.5,
};

export function normalizeBengaliDigits(input: string): string {
  return input.replace(/[০-৯]/g, (ch) => BENGALI_DIGIT_MAP[ch] ?? ch);
}

export function extractBengaliAmount(rawText: string): number {
  const normalized = normalizeBengaliDigits(rawText).replace(/,/g, '');

  // Check patterns like "5 হাজার", "12 শত", "1.5 লাখ"
  const scaledNumeric = normalized.match(
    /(\d+(?:\.\d+)?)\s*(হাজার|hajar|k|শত|শো|shoto|লাখ|লক্ষ|lakh)/i
  );
  if (scaledNumeric) {
    const base = parseFloat(scaledNumeric[1]);
    const unit = scaledNumeric[2].toLowerCase();
    if (!Number.isNaN(base)) {
      if (unit === 'হাজার' || unit === 'hajar' || unit === 'k') {
        return Math.round(base * 1000);
      }
      if (unit === 'শত' || unit === 'শো' || unit === 'shoto') {
        return Math.round(base * 100);
      }
      if (unit === 'লাখ' || unit === 'লক্ষ' || unit === 'lakh') {
        return Math.round(base * 100000);
      }
    }
  }

  // Prefer numbers immediately near currency/amount keywords
  const currencyMatch = normalized.match(
    /(\d+(?:\.\d+)?)\s*(?:টাকা|taka|tk|bdt|৳)|(?:৳|taka|tk)\s*(\d+(?:\.\d+)?)/i
  );
  if (currencyMatch) {
    const val = parseFloat(currencyMatch[1] || currencyMatch[2]);
    if (!Number.isNaN(val) && val > 0) return Math.round(val);
  }

  // Spoken Bengali word multipliers (e.g., "পাঁচ হাজার", "বারো শত", "দেড় হাজার")
  const tokens = normalized.split(/\s+/);
  for (let i = 0; i < tokens.length; i++) {
    const word = tokens[i].replace(/[.,!?।]/g, '');
    const next = (tokens[i + 1] || '').replace(/[.,!?।]/g, '');
    const wordVal = BENGALI_WORD_NUMBERS[word];
    if (wordVal !== undefined) {
      if (next === 'হাজার' || next === 'hajar') return Math.round(wordVal * 1000);
      if (next === 'শত' || next === 'শো' || next === 'শ') return Math.round(wordVal * 100);
      if (next === 'লাখ' || next === 'লক্ষ') return Math.round(wordVal * 100000);
      return Math.round(wordVal);
    }
  }

  // Fallback: any standalone number in text
  const anyNum = normalized.match(/\b(\d+(?:\.\d+)?)\b/);
  if (anyNum) {
    const val = parseFloat(anyNum[1]);
    if (!Number.isNaN(val) && val > 0) return Math.round(val);
  }

  return 0;
}

function getDhakaTodayIso(baseDate?: string): string {
  if (baseDate && /^\d{4}-\d{2}-\d{2}$/.test(baseDate)) return baseDate;
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Dhaka',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function shiftDateIso(isoDate: string, deltaDays: number): string {
  const [y, m, d] = isoDate.split('-').map((n) => parseInt(n, 10));
  const dt = new Date(Date.UTC(y, m - 1, d + deltaDays));
  return dt.toISOString().slice(0, 10);
}

export function parseBengaliFinanceText(
  text: string,
  context?: VoiceParseContext
): ParsedFinanceDraft {
  const cleanText = text.trim();
  const lower = normalizeBengaliDigits(cleanText).toLowerCase();
  const todayIso = getDhakaTodayIso(context?.todayDateStr);

  // 1. Date
  let date = todayIso;
  if (/গত\s*পরশু|day before yesterday/i.test(lower)) {
    date = shiftDateIso(todayIso, -2);
  } else if (/গতকাল|yesterday|কালকে/i.test(lower)) {
    date = shiftDateIso(todayIso, -1);
  }

  // 2. Amount
  const amount = extractBengaliAmount(cleanText);

  // 3. Transaction Type
  let type: ParsedFinanceDraft['type'] = 'expense';
  if (/ধার\s*(?:ফেরত\s*)?পেয়েছি|ধার\s*তুলেছি|loan\s*repayment\s*received/i.test(lower)) {
    type = 'loan_repayment_received';
  } else if (/ঋণ\s*শোধ|ধার\s*শোধ|কিস্তি\s*দিয়েছি|loan\s*repayment\s*paid/i.test(lower)) {
    type = 'loan_repayment_paid';
  } else if (/ধার\s*দিয়েছি|ধার\s*দিলাম|হাওলাত\s*দিয়েছি|lent|loan\s*given/i.test(lower)) {
    type = 'loan_given';
  } else if (/ধার\s*নিয়েছি|ঋণ\s*নিয়েছি|borrowed|loan\s*taken|loan\s*received/i.test(lower)) {
    type = 'loan_received';
  } else if (/ট্রান্সফার|transfer|পাঠিয়েছি\s*নিজের/i.test(lower)) {
    type = 'transfer';
  } else if (/ডিপিএস|dps|সঞ্চয়\s*জমা|সেভিংস\s*জমা|savings\s*deposit/i.test(lower)) {
    type = 'savings_deposit';
  } else if (/রিফান্ড|ফেরত\s*এসেছে|refund/i.test(lower)) {
    type = 'refund';
  } else if (
    /বেতন|স্যালারি|salary|আয়|ইনকাম|income|বোনাস|bonus|পেয়েছি|রিসিভ|received|ভাতা|freelance|ব্যবসা\s*থেকে/i.test(
      lower
    ) &&
    !/খরচ|কিনেছি|দিয়েছি|ভাড়া|বিল/i.test(lower)
  ) {
    type = 'income';
  }

  // 4. Account matching (check user's accounts first, then standard BD channels)
  let accountName = 'Cash';
  let paymentMethod = 'Cash';
  const accountKeywords: Array<{ pattern: RegExp; canonical: string }> = [
    { pattern: /বিকাশ|bkash|b-kash/i, canonical: 'bKash' },
    { pattern: /নগদ|nagad/i, canonical: 'Nagad' },
    { pattern: /রকেট|rocket/i, canonical: 'Rocket' },
    { pattern: /উপায়|upay/i, canonical: 'Upay' },
    { pattern: /ডাচ\s*বাংলা|dbbl/i, canonical: 'DBBL' },
    { pattern: /সিটি\s*ব্যাংক|city\s*bank/i, canonical: 'City Bank' },
    { pattern: /ব্র্যাক|brac/i, canonical: 'BRAC Bank' },
    { pattern: /ইসলামী\s*ব্যাংক|islami\s*bank/i, canonical: 'Islami Bank' },
    { pattern: /ব্যাংক|bank|কার্ড|card/i, canonical: 'Bank' },
    { pattern: /ক্যাশ|হাতে|cash/i, canonical: 'Cash' },
  ];

  // Try matching directly against user's context accounts first
  const userAccounts = context?.accounts || [];
  let matchedUserAcc = userAccounts.find(
    (a) =>
      lower.includes(a.name.toLowerCase()) ||
      (a.provider && lower.includes(a.provider.toLowerCase()))
  );

  if (!matchedUserAcc) {
    for (const entry of accountKeywords) {
      if (entry.pattern.test(lower)) {
        accountName = entry.canonical;
        paymentMethod = entry.canonical;
        matchedUserAcc = userAccounts.find(
          (a) =>
            a.name.toLowerCase().includes(entry.canonical.toLowerCase()) ||
            (a.provider && a.provider.toLowerCase().includes(entry.canonical.toLowerCase()))
        );
        if (matchedUserAcc) {
          accountName = matchedUserAcc.name;
          paymentMethod = matchedUserAcc.provider || entry.canonical;
        }
        break;
      }
    }
  } else {
    accountName = matchedUserAcc.name;
    paymentMethod = matchedUserAcc.provider || matchedUserAcc.name;
  }

  // 5. Category matching
  let categoryName = type === 'income' ? 'Salary' : 'Other';
  const categoryRules: Array<{ pattern: RegExp; cat: string }> = [
    { pattern: /বাজার|খাবার|নাস্তা|লাঞ্চ|ডিনার|মাছ|মাংস|চাল|সবজি|চা|কফি|food|grocery|bazar|restaurant/i, cat: 'Food & Grocery' },
    { pattern: /বাসা\s*ভাড়া|বাড়ি\s*ভাড়া|রেন্ট|rent|house\s*rent/i, cat: 'House Rent' },
    { pattern: /রিকশা|সিএনজি|বাস|অটো|উবার|পাঠাও|গাড়ি|ভাড়া|তেল|যাতায়াত|transport|rickshaw|uber|cng/i, cat: 'Transportation' },
    { pattern: /বিদ্যুৎ|গ্যাস|পানি|ইন্টারনেট|ওয়াইফাই|wifi|বিল|রিচার্জ|মোবাইল\s*বিল|utility|utilities|bill/i, cat: 'Utilities' },
    { pattern: /ওষুধ|ডাক্তার|হাসপাতাল|মেডিকেল|চিকিৎসা|medicine|medical|doctor/i, cat: 'Medical' },
    { pattern: /শপিং|কাপড়|জামা|জুতা|কেনাকাটা|shopping|cloth/i, cat: 'Shopping' },
    { pattern: /স্কুল|কলেজ|ভার্সিটি|বই|টিউশন|পড়ালেখা|education|tuition/i, cat: 'Education' },
    { pattern: /বেতন|স্যালারি|salary|বোনাস/i, cat: 'Salary' },
    { pattern: /ব্যবসা|বিক্রি|business/i, cat: 'Business' },
    { pattern: /পরিবার|ফ্যামিলি|সংসার|family/i, cat: 'Family Expense' },
  ];

  const userCategories = context?.categories || [];
  const directCat = userCategories.find(
    (c) =>
      lower.includes(c.name.toLowerCase()) ||
      (c.nameBn && cleanText.includes(c.nameBn))
  );
  if (directCat) {
    categoryName = directCat.name;
  } else {
    for (const rule of categoryRules) {
      if (rule.pattern.test(lower)) {
        const ctxMatch = userCategories.find(
          (c) =>
            c.name.toLowerCase().includes(rule.cat.toLowerCase()) ||
            (c.nameBn && c.nameBn.includes(rule.cat))
        );
        categoryName = ctxMatch ? ctxMatch.name : rule.cat;
        break;
      }
    }
  }

  // 6. Person / Beneficiary matching
  let personName = '';
  const userPeople = context?.people || [];
  const directPerson = userPeople.find(
    (p) =>
      lower.includes(p.name.toLowerCase()) ||
      (p.nameBn && cleanText.includes(p.nameBn))
  );
  if (directPerson) {
    personName = directPerson.name;
  } else if (/পরিবার|ফ্যামিলি|সংসার|family/i.test(lower)) {
    personName = 'Family';
  } else if (/স্ত্রী|বউ|wife/i.test(lower)) {
    personName = 'Wife';
  } else if (/আম্মা|মা\b|mother|mom/i.test(lower)) {
    personName = 'Mother';
  } else if (/আব্বা|বাবা|father|dad/i.test(lower)) {
    personName = 'Father';
  } else if (/ব্যক্তিগত|personal/i.test(lower)) {
    personName = 'Personal';
  } else {
    const friendNameMatch = cleanText.match(/(?:বন্ধু|দোস্ত)\s+([^\s,।]+)/);
    if (friendNameMatch?.[1]) {
      personName = friendNameMatch[1].replace(/(?:কে|ের|র)$/, '');
    }
  }

  return {
    transcript: cleanText,
    type,
    amount,
    categoryName,
    personName,
    paymentMethod,
    accountName,
    description: cleanText,
    note: `Voice input (${cleanText})`,
    date,
    confidence: amount > 0 ? 0.86 : 0.55,
    source: 'local-parser',
  };
}
