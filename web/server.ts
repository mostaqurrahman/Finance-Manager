import express from 'express';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import { GoogleGenAI } from '@google/genai';
import { parseBengaliFinanceText } from './src/services/bengaliVoiceParser';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3000;

app.use(express.json({ limit: '15mb' }));

// Server-side Gemini API + Smart Bengali Parser for Voice Transaction Extraction
app.post('/api/parse-transaction', async (req, res) => {
  try {
    const { text, audioBase64, audioMimeType, context } = req.body;
    const hasText = typeof text === 'string' && text.trim().length > 0;
    const hasAudio = typeof audioBase64 === 'string' && audioBase64.length > 0;

    if (!hasText && !hasAudio) {
      return res.status(400).json({ error: 'Text prompt or recorded audio is required' });
    }

    const todayStr = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Dhaka',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date());

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      if (hasText) {
        const localParsed = parseBengaliFinanceText(text, {
          ...context,
          todayDateStr: todayStr,
        });
        return res.json(localParsed);
      }
      return res.status(422).json({
        error:
          'Audio transcription requires GEMINI_API_KEY in AI Studio Secrets, or please speak using your browser microphone / type in Bengali below.',
        requiresTextInput: true,
      });
    }

    const ai = new GoogleGenAI({ apiKey });

    const prompt = `
You are a financial parsing assistant specialized in Bangladeshi Bengali (বাংলা), Banglish, and English personal finance terminology.
${hasAudio ? 'First transcribe the spoken Bengali/English audio accurately into "transcript", then extract structured transaction fields.' : 'Analyze the following user voice transcription or natural text and extract structured financial transaction information.'}

${hasText ? `User Input: "${text}"` : ''}
Current date in Asia/Dhaka: ${todayStr}.

Context available in user's system:
- Known accounts: ${JSON.stringify(context?.accounts || [])}
- Known categories: ${JSON.stringify(context?.categories || [])}
- Known people/beneficiaries: ${JSON.stringify(context?.people || [])}

Rules:
1. Transaction types can be: "income", "expense", "transfer", "loan_given", "loan_received", "loan_repayment_received", "loan_repayment_paid", "savings_deposit", "savings_withdrawal", "refund".
   Default is "expense" unless income, transfer, or loan keywords are evident.
2. Amount: Extract numeric amount in Bangladeshi Taka (BDT). Convert Bengali numerals (১, ২, ৩...) or words (বারো শত -> 1200, পাঁচ হাজার -> 5000) to pure number.
3. Category: Match against known categories or standard categories (Food & Grocery, House Rent, Utilities, Transportation, Shopping, Medical, Education, Family Expense, Salary, Business, etc.).
4. Person: If mentioned (e.g. "Family", "Wife", "আম্মা", "বাবা", "বন্ধু শাকিল"), extract or match.
5. Account: Common Bangladeshi channels: "bKash" (বিকাশ), "Nagad" (নগদ), "Rocket" (রকেট), "DBBL", "Bank" (ব্যাংক), "Cash" (ক্যাশ).
6. Date: "আজকে"/"today" -> ${todayStr}. Otherwise YYYY-MM-DD.

Respond ONLY with valid JSON:
{
  "transcript": "Recognized Bengali/English sentence",
  "type": "expense",
  "amount": 1200,
  "categoryName": "Food & Grocery",
  "personName": "Family",
  "paymentMethod": "bKash",
  "accountName": "bKash",
  "description": "Short Bengali/English summary",
  "note": "Optional details",
  "date": "${todayStr}",
  "confidence": 0.95
}
`;

    try {
      const contents = hasAudio
        ? [
            {
              inlineData: {
                data: audioBase64,
                mimeType: audioMimeType || 'audio/webm',
              },
            },
            { text: prompt },
          ]
        : prompt;

      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents,
        config: {
          responseMimeType: 'application/json',
        },
      });

      const responseText = response.text || '{}';
      let parsed: any;
      try {
        parsed = JSON.parse(responseText);
      } catch {
        const cleaned = responseText.replace(/```json/g, '').replace(/```/g, '').trim();
        parsed = JSON.parse(cleaned);
      }

      return res.json({
        transcript: parsed.transcript || (hasText ? text : ''),
        ...parsed,
        source: 'gemini',
      });
    } catch (geminiErr: any) {
      console.warn('Gemini parse failed, falling back to local parser if text available:', geminiErr?.message);
      if (hasText) {
        const localParsed = parseBengaliFinanceText(text, {
          ...context,
          todayDateStr: todayStr,
        });
        return res.json(localParsed);
      }
      throw geminiErr;
    }
  } catch (error: any) {
    console.error('Error in /api/parse-transaction:', error);
    return res.status(500).json({
      error: error.message || 'Failed to parse transaction',
    });
  }
});

// Server-side Gemini API endpoint for Dashboard AI Spending & Savings Insights
app.post('/api/ai-insights', async (req, res) => {
  try {
    const digest = req.body;
    if (!digest || typeof digest !== 'object' || !Array.isArray(digest.categoryBreakdown)) {
      return res.status(400).json({ error: 'Valid financial digest payload is required.' });
    }

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return res.status(422).json({
        error:
          'GEMINI_API_KEY is not configured in AI Studio Secrets. Please set GEMINI_API_KEY to enable Gemini AI financial insights.',
        requiresApiKey: true,
      });
    }

    const isBn = digest.language === 'bn';
    const ai = new GoogleGenAI({ apiKey });

    const prompt = `
You are an insightful personal finance advisor for a user in Bangladesh (currency BDT ৳).
Analyze the following actual aggregated financial metrics for the period "${digest.periodLabel || 'Selected Period'}" and produce clear, data-backed spending pattern insights and realistic potential savings category recommendations.

User Financial Digest (all amounts in BDT):
- Total Income: ${digest.totalIncome}
- Total Net Expense: ${digest.totalExpense}
- Net Cash Flow: ${digest.netCashFlow}
- Savings Rate: ${digest.savingsRatePercent}%
- Transaction Count: ${digest.txCount}
- Category Spending Breakdown: ${JSON.stringify(digest.categoryBreakdown)}
- Top Expense Records: ${JSON.stringify(digest.topExpenses || [])}
- Active Category Budgets: ${JSON.stringify(digest.budgetStatuses || [])}

Instructions:
1. Respond in ${isBn ? 'natural, clear Bengali (বাংলা)' : 'clear, concise English'}.
2. Reference ONLY real numbers and categories present in the provided financial digest—never invent transactions or fake category names.
3. Highlight 2 to 4 concrete spending patterns (e.g., dominant categories, budget pressure, savings rate health, frequent small vs large expenses).
4. Highlight 2 to 4 potential savings categories where reducing discretionary spend is realistic, estimating a conservative "estimatedMonthlySavings" in BDT (between 5% and 25% of that category's spend, 0 if totalExpense is 0).
5. Compute "savingsPotentialTotal" as the sum of "estimatedMonthlySavings" across your recommended categories.

Respond ONLY with valid JSON matching this exact structure:
{
  "headline": "Short 1-line executive takeaway",
  "spendingSummary": "2-3 sentence summary of overall spending patterns and cash-flow health",
  "patterns": [
    {
      "title": "Short pattern title",
      "detail": "Specific observation referencing actual figures",
      "trend": "up"
    }
  ],
  "savingsOpportunities": [
    {
      "categoryName": "Exact category name from digest",
      "currentSpend": 5000,
      "estimatedMonthlySavings": 750,
      "actionableTip": "Practical saving recommendation",
      "priority": "high"
    }
  ],
  "savingsPotentialTotal": 750
}
Allowed values: trend in ["up", "down", "stable"], priority in ["high", "medium", "low"].
`;

    const modelName = 'gemini-2.5-flash';
    const response = await ai.models.generateContent({
      model: modelName,
      contents: prompt,
      config: {
        responseMimeType: 'application/json',
      },
    });

    const rawText = response.text || '{}';
    let parsed: any;
    try {
      parsed = JSON.parse(rawText);
    } catch {
      const cleaned = rawText.replace(/```json/g, '').replace(/```/g, '').trim();
      parsed = JSON.parse(cleaned);
    }

    const opportunities = Array.isArray(parsed.savingsOpportunities)
      ? parsed.savingsOpportunities.map((item: any) => ({
          categoryName: String(item?.categoryName || 'General'),
          currentSpend: Number(item?.currentSpend) || 0,
          estimatedMonthlySavings: Math.max(0, Math.round(Number(item?.estimatedMonthlySavings) || 0)),
          actionableTip: String(item?.actionableTip || ''),
          priority:
            item?.priority === 'high' || item?.priority === 'low' ? item.priority : 'medium',
        }))
      : [];

    const totalPotential =
      Number(parsed.savingsPotentialTotal) > 0
        ? Math.round(Number(parsed.savingsPotentialTotal))
        : opportunities.reduce((sum: number, o: any) => sum + o.estimatedMonthlySavings, 0);

    return res.json({
      headline: String(parsed.headline || ''),
      spendingSummary: String(parsed.spendingSummary || ''),
      patterns: Array.isArray(parsed.patterns)
        ? parsed.patterns.map((p: any) => ({
            title: String(p?.title || ''),
            detail: String(p?.detail || ''),
            trend: p?.trend === 'up' || p?.trend === 'down' ? p.trend : 'stable',
          }))
        : [],
      savingsOpportunities: opportunities,
      savingsPotentialTotal: totalPotential,
      generatedAt: new Date().toISOString(),
      model: modelName,
    });
  } catch (error: any) {
    console.error('Error in /api/ai-insights:', error);
    return res.status(500).json({
      error: error?.message || 'Failed to generate Gemini AI insights',
    });
  }
});

// Health check endpoint
app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok', time: new Date().toISOString() });
});

// Vite Middleware for development
async function startServer() {
  const isProduction = process.env.NODE_ENV === 'production';

  if (!isProduction) {
    const { createServer } = await import('vite');
    const vite = await createServer({
      root: path.resolve(__dirname),
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    // Serve static files in production
    app.use(express.static(path.resolve(__dirname, 'dist')));
    app.get('*', (_req, res) => {
      res.sendFile(path.resolve(__dirname, 'dist', 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running at http://0.0.0.0:${PORT} in ${isProduction ? 'production' : 'development'} mode`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
