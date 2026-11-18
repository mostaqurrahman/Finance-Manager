import React, { useState, useEffect, useRef } from 'react';
import { useFinance } from '../context/FinanceContext';
import { useLanguage } from '../context/LanguageContext';
import { getCurrentDhakaDateString } from '../i18n/formatters';
import {
  ParsedFinanceDraft,
  parseBengaliFinanceText,
} from '../services/bengaliVoiceParser';
import {
  Mic,
  MicOff,
  Sparkles,
  Check,
  AlertCircle,
  X,
  Loader2,
  RotateCcw,
  Edit3,
  Square,
  Volume2,
} from 'lucide-react';

interface VoiceTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface ReviewDraftState {
  type: string;
  amount: number;
  accountId: string;
  destinationAccountId?: string;
  categoryId?: string;
  personId?: string;
  date: string;
  description: string;
  note: string;
  parseSource?: string;
}

const SAMPLE_VOICE_PHRASES = [
  'আজকে বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ করেছি, Family Expense এ রাখো।',
  'অফিস থেকে বেতন পেয়েছি ৬০,০০০ টাকা ব্যাংক একাউন্টে।',
  'বন্ধু শাকিলকে ক্যাশ থেকে ৩০০০ টাকা ধার দিয়েছি।',
  'রিকশা ভাড়া দিয়েছি ৮০ টাকা নগদ থেকে।',
];

export const VoiceTransactionModal: React.FC<VoiceTransactionModalProps> = ({
  isOpen,
  onClose,
}) => {
  const { accounts, categories, people, createTransaction } = useFinance();
  const { t, language } = useLanguage();

  const [speechLang, setSpeechLang] = useState<'bn-BD' | 'en-US'>('bn-BD');
  const [isListening, setIsListening] = useState(false);
  const [isRecordingAudio, setIsRecordingAudio] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [interimText, setInterimText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [speechSupported, setSpeechSupported] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [infoTip, setInfoTip] = useState<string | null>(null);

  const [reviewData, setReviewData] = useState<ReviewDraftState | null>(null);

  const recognitionRef = useRef<any>(null);
  const finalBufferRef = useRef<string>('');
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);

  // Reset transient listening when modal closes
  useEffect(() => {
    if (!isOpen) {
      if (recognitionRef.current && isListening) {
        try {
          recognitionRef.current.stop();
        } catch {
          // ignore
        }
      }
      if (mediaRecorderRef.current && isRecordingAudio) {
        try {
          mediaRecorderRef.current.stop();
        } catch {
          // ignore
        }
      }
      setIsListening(false);
      setIsRecordingAudio(false);
      setInterimText('');
    }
  }, [isOpen, isListening, isRecordingAudio]);

  // Configure Web Speech Recognition safely whenever speechLang changes
  useEffect(() => {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setSpeechSupported(false);
      recognitionRef.current = null;
      return;
    }

    setSpeechSupported(true);
    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;
    recognition.lang = speechLang;

    recognition.onstart = () => {
      setIsListening(true);
      setError(null);
      setInfoTip(null);
    };

    recognition.onresult = (event: any) => {
      let finalized = '';
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const segment = event.results[i][0]?.transcript || '';
        if (event.results[i].isFinal) {
          finalized += (finalized ? ' ' : '') + segment.trim();
        } else {
          interim += segment;
        }
      }

      if (finalized) {
        const combined = [finalBufferRef.current, finalized].filter(Boolean).join(' ').trim();
        finalBufferRef.current = combined;
        setTranscript(combined);
      }
      setInterimText(interim);
    };

    recognition.onerror = (event: any) => {
      console.warn('Speech recognition error:', event.error);
      setIsListening(false);
      setInterimText('');
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setError(
          language === 'bn'
            ? 'মাইক্রোফোন অনুমতি দেওয়া হয়নি। নিচের টেক্সট বক্সে বাংলায় লিখুন অথবা ব্রাউজার অনুমতি দিন।'
            : 'Microphone permission denied. Enable mic permission or type/edit text below.'
        );
      } else if (event.error === 'language-not-supported') {
        setSpeechSupported(false);
        setError(
          language === 'bn'
            ? 'এই ব্রাউজারে সরাসরি বাংলা স্পিচ সাপোর্ট নেই—অডিও রেকর্ড বা বাংলা টেক্সট ব্যবহার করুন।'
            : 'Browser speech recognition does not support bn-BD here. Use Audio Record or type below.'
        );
      } else if (event.error !== 'no-speech' && event.error !== 'aborted') {
        setError(
          language === 'bn'
            ? `ভয়েস ইনপুট ত্রুটি (${event.error})। আপনি নিচে লিখেও বিশ্লেষণ করতে পারবেন।`
            : `Voice error (${event.error}). You can edit or type directly below.`
        );
      }
    };

    recognition.onend = () => {
      setIsListening(false);
      setInterimText('');
    };

    recognitionRef.current = recognition;

    return () => {
      try {
        recognition.abort();
      } catch {
        // ignore
      }
    };
  }, [speechLang, language]);

  if (!isOpen) return null;

  const mapParsedToReviewData = (
    parsed: Partial<ParsedFinanceDraft>,
    rawText: string
  ): ReviewDraftState => {
    // Match account
    let matchedAccountId = accounts[0]?.id || '';
    if (parsed.accountName) {
      const accNeedle = parsed.accountName.toLowerCase();
      const found = accounts.find(
        (a) =>
          a.name.toLowerCase().includes(accNeedle) ||
          a.provider.toLowerCase().includes(accNeedle) ||
          accNeedle.includes(a.provider.toLowerCase())
      );
      if (found) matchedAccountId = found.id;
    }

    // Match category
    let matchedCategoryId: string | undefined = undefined;
    if (parsed.categoryName) {
      const catNeedle = parsed.categoryName.toLowerCase();
      const foundCat = categories.find(
        (c) =>
          c.name.toLowerCase().includes(catNeedle) ||
          catNeedle.includes(c.name.toLowerCase()) ||
          c.nameBn.includes(parsed.categoryName!)
      );
      if (foundCat) matchedCategoryId = foundCat.id;
    }

    // Match person
    let matchedPersonId: string | undefined = undefined;
    if (parsed.personName) {
      const personNeedle = parsed.personName.toLowerCase();
      const foundPerson = people.find(
        (p) =>
          p.name.toLowerCase().includes(personNeedle) ||
          personNeedle.includes(p.name.toLowerCase()) ||
          p.nameBn.includes(parsed.personName!)
      );
      if (foundPerson) matchedPersonId = foundPerson.id;
    }

    return {
      type: parsed.type || 'expense',
      amount: Math.round(parsed.amount || 0),
      accountId: matchedAccountId,
      destinationAccountId: accounts.find((a) => a.id !== matchedAccountId)?.id,
      categoryId: matchedCategoryId,
      personId: matchedPersonId,
      date: parsed.date || getCurrentDhakaDateString(),
      description: parsed.description || rawText,
      note: parsed.note || `Voice input: "${rawText}"`,
      parseSource: parsed.source || 'gemini',
    };
  };

  const toggleWebSpeechListen = () => {
    if (!recognitionRef.current) {
      setError(
        language === 'bn'
          ? 'ব্রাউজার স্পিচ রিকগনিশন পাওয়া যায়নি। অডিও রেকর্ড বা নিচে টেক্সট লিখুন।'
          : 'Browser speech recognition not available. Try Audio Record or type text below.'
      );
      return;
    }

    if (isListening) {
      recognitionRef.current.stop();
      setIsListening(false);
    } else {
      // Keep any user-typed text so appending works seamlessly!
      finalBufferRef.current = transcript.trim();
      setInterimText('');
      setError(null);
      try {
        recognitionRef.current.lang = speechLang;
        recognitionRef.current.start();
      } catch (err) {
        console.warn('Recognition start error:', err);
        setError(
          language === 'bn'
            ? 'মাইক্রোফোন চালু করা যায়নি। নিচে লিখে বা অডিও রেকর্ড করে চেষ্টা করুন।'
            : 'Could not start speech microphone. Try Audio Record or type below.'
        );
      }
    }
  };

  const toggleMediaAudioRecord = async () => {
    if (isRecordingAudio && mediaRecorderRef.current) {
      mediaRecorderRef.current.stop();
      setIsRecordingAudio(false);
      return;
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setError(
        language === 'bn'
          ? 'এই ব্রাউজারে অডিও রেকর্ডিং সাপোর্টেড নয়।'
          : 'Audio recording is not supported in this browser.'
      );
      return;
    }

    try {
      setError(null);
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported('audio/webm')
        ? 'audio/webm'
        : 'audio/mp4';
      const recorder = new MediaRecorder(stream, { mimeType });
      audioChunksRef.current = [];

      recorder.ondataavailable = (evt) => {
        if (evt.data.size > 0) {
          audioChunksRef.current.push(evt.data);
        }
      };

      recorder.onstop = async () => {
        stream.getTracks().forEach((track) => track.stop());
        const blob = new Blob(audioChunksRef.current, { type: mimeType });
        if (blob.size < 100) return;
        await parseAudioBlob(blob, mimeType);
      };

      mediaRecorderRef.current = recorder;
      recorder.start();
      setIsRecordingAudio(true);
    } catch (err: any) {
      setError(
        language === 'bn'
          ? 'মাইক্রোফোন অ্যাক্সেস পাওয়া যায়নি। ব্রাউজার পারমিশন চেক করুন।'
          : err?.message || 'Microphone access denied.'
      );
    }
  };

  const parseAudioBlob = async (blob: Blob, mimeType: string) => {
    setIsProcessing(true);
    setError(null);

    try {
      const arrayBuf = await blob.arrayBuffer();
      const bytes = new Uint8Array(arrayBuf);
      let binary = '';
      for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
      }
      const audioBase64 = btoa(binary);

      const res = await fetch('/api/parse-transaction', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          audioBase64,
          audioMimeType: mimeType,
          text: transcript.trim() || undefined,
          context: {
            accounts: accounts.map((a) => ({ id: a.id, name: a.name, provider: a.provider })),
            categories: categories.map((c) => ({
              id: c.id,
              name: c.name,
              nameBn: c.nameBn,
              type: c.type,
            })),
            people: people.map((p) => ({ id: p.id, name: p.name, nameBn: p.nameBn })),
          },
        }),
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || 'Could not process audio recording.');
      }

      const finalTranscript = data.transcript || transcript;
      if (finalTranscript) {
        setTranscript(finalTranscript);
        finalBufferRef.current = finalTranscript;
      }
      setReviewData(mapParsedToReviewData(data, finalTranscript));
    } catch (err: any) {
      setError(err?.message || 'Audio parsing failed. You can type/edit Bengali text below.');
    } finally {
      setIsProcessing(false);
    }
  };

  const parseTextToDraft = async (overrideText?: string) => {
    const combinedText = (overrideText ?? [transcript, interimText].filter(Boolean).join(' ')).trim();
    if (!combinedText) {
      setError(
        language === 'bn'
          ? 'প্রথমে বাংলায় বলুন বা নিচে টেক্সট লিখুন।'
          : 'Please speak or enter text first.'
      );
      return;
    }

    if (isListening && recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {
        // ignore
      }
      setIsListening(false);
    }

    setTranscript(combinedText);
    finalBufferRef.current = combinedText;
    setInterimText('');
    setIsProcessing(true);
    setError(null);
    setInfoTip(null);

    const payloadContext = {
      accounts: accounts.map((a) => ({ id: a.id, name: a.name, provider: a.provider })),
      categories: categories.map((c) => ({
        id: c.id,
        name: c.name,
        nameBn: c.nameBn,
        type: c.type,
      })),
      people: people.map((p) => ({ id: p.id, name: p.name, nameBn: p.nameBn })),
    };

    try {
      const res = await fetch('/api/parse-transaction', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text: combinedText,
          context: payloadContext,
        }),
      });

      if (!res.ok) {
        throw new Error('Fallback to local parser');
      }

      const parsed = await res.json();
      setReviewData(mapParsedToReviewData(parsed, combinedText));
    } catch {
      // Seamless local client-side fallback so Bangla voice ALWAYS works even offline!
      const localParsed = parseBengaliFinanceText(combinedText, payloadContext);
      setReviewData(mapParsedToReviewData(localParsed, combinedText));
      setInfoTip(
        language === 'bn'
          ? 'স্মার্ট বাংলা পার্সার দিয়ে তথ্য সাজানো হয়েছে — প্রয়োজনে নিচের যেকোনো ঘর পরিবর্তন করে সেভ করুন।'
          : 'Parsed with built-in Bengali smart parser — modify any field below before saving.'
      );
    } finally {
      setIsProcessing(false);
    }
  };

  const handleOpenManualModify = () => {
    const combinedText = [transcript, interimText].filter(Boolean).join(' ').trim();
    const localParsed = parseBengaliFinanceText(combinedText || 'খরচ', {
      accounts: accounts.map((a) => ({ id: a.id, name: a.name, provider: a.provider })),
      categories: categories.map((c) => ({
        id: c.id,
        name: c.name,
        nameBn: c.nameBn,
        type: c.type,
      })),
      people: people.map((p) => ({ id: p.id, name: p.name, nameBn: p.nameBn })),
    });
    setReviewData(mapParsedToReviewData(localParsed, combinedText));
  };

  const handleSaveConfirmed = async () => {
    if (!reviewData) return;
    if (reviewData.amount <= 0) {
      setError(t('invalidAmount'));
      return;
    }

    try {
      await createTransaction({
        type: reviewData.type as any,
        amount: reviewData.amount,
        accountId: reviewData.accountId,
        ...(reviewData.type === 'transfer' && reviewData.destinationAccountId
          ? { destinationAccountId: reviewData.destinationAccountId }
          : {}),
        categoryId: reviewData.categoryId || undefined,
        personId: reviewData.personId || undefined,
        paymentMethod:
          accounts.find((a) => a.id === reviewData.accountId)?.provider || 'Cash',
        occurredAt: reviewData.date,
        timezone: 'Asia/Dhaka',
        description: reviewData.description.trim() || transcript.trim(),
        note: reviewData.note,
        status: 'posted',
      });

      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to save transaction');
    }
  };

  const displayTranscript = interimText
    ? `${transcript}${transcript ? ' ' : ''}${interimText}`
    : transcript;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="w-full max-w-2xl bg-white dark:bg-slate-900 rounded-3xl shadow-2xl border border-slate-200 dark:border-slate-800 overflow-hidden my-8">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-800 dark:text-white">
                {t('voiceTitle')}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                বাংলা / Banglish ভয়েস + এডিটেবল রিভিউ (Speech &amp; Smart AI Parser)
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Language selector for recognition */}
            <div className="flex rounded-xl bg-slate-100 dark:bg-slate-800 p-0.5 text-[11px] font-bold">
              <button
                type="button"
                onClick={() => setSpeechLang('bn-BD')}
                className={`px-2.5 py-1 rounded-lg transition-colors ${
                  speechLang === 'bn-BD'
                    ? 'bg-white dark:bg-slate-700 text-emerald-600 shadow-sm'
                    : 'text-slate-500'
                }`}
              >
                বাংলা
              </button>
              <button
                type="button"
                onClick={() => setSpeechLang('en-US')}
                className={`px-2.5 py-1 rounded-lg transition-colors ${
                  speechLang === 'en-US'
                    ? 'bg-white dark:bg-slate-700 text-emerald-600 shadow-sm'
                    : 'text-slate-500'
                }`}
              >
                EN
              </button>
            </div>

            <button
              onClick={onClose}
              className="p-2 rounded-xl text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="p-6 space-y-5 max-h-[80vh] overflow-y-auto">
          {error && (
            <div className="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {infoTip && (
            <div className="p-3 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200 text-xs">
              {infoTip}
            </div>
          )}

          {!reviewData ? (
            <div className="flex flex-col items-center text-center space-y-4">
              <p className="text-xs text-slate-600 dark:text-slate-300 max-w-md">
                {t('voiceInstructions')}
              </p>

              {/* Voice capture controls */}
              <div className="flex items-center justify-center gap-4 my-1">
                {speechSupported && (
                  <div className="relative flex flex-col items-center gap-1.5">
                    <div className="relative">
                      {isListening && (
                        <span className="absolute -inset-3 rounded-full bg-emerald-500/30 animate-ping" />
                      )}
                      <button
                        type="button"
                        onClick={toggleWebSpeechListen}
                        disabled={isRecordingAudio || isProcessing}
                        className={`relative w-20 h-20 rounded-full flex items-center justify-center text-white shadow-lg transition-transform active:scale-95 ${
                          isListening
                            ? 'bg-rose-600 shadow-rose-600/40'
                            : 'bg-emerald-600 shadow-emerald-600/40 hover:scale-105'
                        }`}
                      >
                        {isListening ? (
                          <MicOff className="w-9 h-9" />
                        ) : (
                          <Mic className="w-9 h-9" />
                        )}
                      </button>
                    </div>
                    <span className="text-[11px] font-bold text-slate-600 dark:text-slate-300">
                      {isListening ? 'Stop Listening' : 'Live Bangla Speech'}
                    </span>
                  </div>
                )}

                {/* Secondary Audio Clip Recorder */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    type="button"
                    onClick={toggleMediaAudioRecord}
                    disabled={isListening || isProcessing}
                    className={`w-14 h-14 rounded-2xl flex items-center justify-center border-2 transition-all ${
                      isRecordingAudio
                        ? 'bg-rose-600 border-rose-600 text-white animate-pulse'
                        : 'border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                    title="Record audio clip for AI transcription"
                  >
                    {isRecordingAudio ? (
                      <Square className="w-5 h-5" />
                    ) : (
                      <Volume2 className="w-5 h-5 text-emerald-600" />
                    )}
                  </button>
                  <span className="text-[11px] font-medium text-slate-500">
                    {isRecordingAudio ? 'Stop & Send Audio' : 'Record Clip'}
                  </span>
                </div>
              </div>

              <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                {isListening
                  ? t('listening')
                  : isRecordingAudio
                  ? 'Recording audio clip... tap Stop when done'
                  : 'ট্যাপ করে বাংলায় বলুন, অথবা নিচে লিখে / এডিট করে বিশ্লেষণ করুন'}
              </p>

              {/* Editable Live Transcript / Bengali Text Input */}
              <div className="w-full text-left space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-slate-600 dark:text-slate-300">
                    ভয়েস টেক্সট (প্রয়োজনে সরাসরি পরিবর্তন/এডিট করুন):
                  </label>
                  {transcript && (
                    <button
                      type="button"
                      onClick={() => {
                        setTranscript('');
                        setInterimText('');
                        finalBufferRef.current = '';
                      }}
                      className="text-[11px] font-semibold text-rose-500 hover:underline flex items-center gap-1"
                    >
                      <RotateCcw className="w-3 h-3" />
                      Clear
                    </button>
                  )}
                </div>

                <textarea
                  rows={3}
                  value={displayTranscript}
                  onChange={(e) => {
                    setInterimText('');
                    finalBufferRef.current = e.target.value;
                    setTranscript(e.target.value);
                  }}
                  placeholder="যেমন: আজকে বাজার করার জন্য বিকাশ থেকে ১২০০ টাকা খরচ করেছি, এটা Family Expense হিসেবে রাখো।"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-800 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              {/* Action buttons */}
              <div className="w-full grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <button
                  type="button"
                  onClick={() => parseTextToDraft()}
                  disabled={isProcessing || !displayTranscript.trim()}
                  className="sm:col-span-2 py-3 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-semibold text-sm transition-all flex items-center justify-center gap-2 shadow-md"
                >
                  {isProcessing ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>{t('processingVoice')}</span>
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4" />
                      <span>বিশ্লেষণ ও রিভিউ করুন (Parse)</span>
                    </>
                  )}
                </button>

                <button
                  type="button"
                  onClick={handleOpenManualModify}
                  disabled={isProcessing}
                  className="py-3 px-3 rounded-xl border border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 font-semibold text-xs flex items-center justify-center gap-1.5"
                >
                  <Edit3 className="w-3.5 h-3.5" />
                  <span>সরাসরি এডিট করুন</span>
                </button>
              </div>

              {/* Quick Sample Phrases */}
              <div className="w-full text-left pt-2 space-y-1.5">
                <span className="text-[11px] font-semibold text-slate-400 block">
                  উদাহরণ (ট্যাপ করে টেক্সট বসান বা এডিট করুন):
                </span>
                <div className="grid grid-cols-1 gap-1.5">
                  {SAMPLE_VOICE_PHRASES.map((phrase, i) => (
                    <button
                      key={i}
                      type="button"
                      onClick={() => {
                        setTranscript(phrase);
                        finalBufferRef.current = phrase;
                        setInterimText('');
                      }}
                      className="text-left px-3 py-2 rounded-xl bg-slate-50 dark:bg-slate-800/70 hover:bg-emerald-50 dark:hover:bg-emerald-950/30 border border-slate-200/80 dark:border-slate-700/70 text-xs text-slate-700 dark:text-slate-300 transition-colors"
                    >
                      💡 {phrase}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            /* Fully Editable Review & Modify Screen */
            <div className="space-y-4">
              {/* Inline Transcript Edit & Reparse */}
              <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/70 border border-slate-200 dark:border-slate-700 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-bold text-slate-500">
                    মূল ভয়েস টেক্সট (এডিট করে পুনরায় Parse করতে পারবেন):
                  </span>
                  <button
                    type="button"
                    onClick={() => parseTextToDraft(transcript)}
                    disabled={isProcessing || !transcript.trim()}
                    className="text-[11px] font-bold text-emerald-600 hover:underline flex items-center gap-1"
                  >
                    <Sparkles className="w-3 h-3" />
                    Re-parse Text
                  </button>
                </div>
                <input
                  type="text"
                  value={transcript}
                  onChange={(e) => {
                    finalBufferRef.current = e.target.value;
                    setTranscript(e.target.value);
                  }}
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900 text-xs"
                />
              </div>

              <div className="p-3 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-200 text-xs">
                <strong>{t('voiceReviewTitle')}:</strong> {t('voiceReviewNotice')}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                    {t('fieldAmount')} (৳) *
                  </label>
                  <input
                    type="number"
                    min="1"
                    required
                    value={reviewData.amount || ''}
                    onChange={(e) =>
                      setReviewData({
                        ...reviewData,
                        amount: parseFloat(e.target.value) || 0,
                      })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-base font-bold"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                    লেনদেনের ধরন (Type)
                  </label>
                  <select
                    value={reviewData.type}
                    onChange={(e) =>
                      setReviewData({ ...reviewData, type: e.target.value })
                    }
                    className="w-full px-3 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                  >
                    <option value="expense">{t('typeExpense')}</option>
                    <option value="income">{t('typeIncome')}</option>
                    <option value="transfer">{t('typeTransfer')}</option>
                    <option value="loan_given">{t('typeLoanGiven')}</option>
                    <option value="loan_received">{t('typeLoanReceived')}</option>
                    <option value="savings_deposit">{t('typeSavingsDeposit')}</option>
                    <option value="refund">{t('typeRefund')}</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                    {t('fieldAccount')} *
                  </label>
                  <select
                    value={reviewData.accountId}
                    onChange={(e) =>
                      setReviewData({ ...reviewData, accountId: e.target.value })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                  >
                    {accounts.map((acc) => (
                      <option key={acc.id} value={acc.id}>
                        {acc.name} ({acc.provider})
                      </option>
                    ))}
                  </select>
                </div>

                {reviewData.type === 'transfer' ? (
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                      {t('fieldDestinationAccount')}
                    </label>
                    <select
                      value={reviewData.destinationAccountId || ''}
                      onChange={(e) =>
                        setReviewData({
                          ...reviewData,
                          destinationAccountId: e.target.value,
                        })
                      }
                      className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                    >
                      {accounts
                        .filter((a) => a.id !== reviewData.accountId)
                        .map((acc) => (
                          <option key={acc.id} value={acc.id}>
                            {acc.name} ({acc.provider})
                          </option>
                        ))}
                    </select>
                  </div>
                ) : (
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                      {t('fieldCategory')}
                    </label>
                    <select
                      value={reviewData.categoryId || ''}
                      onChange={(e) =>
                        setReviewData({ ...reviewData, categoryId: e.target.value })
                      }
                      className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                    >
                      <option value="">None / Uncategorized</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {language === 'bn' ? c.nameBn : c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                    {t('fieldPerson')}
                  </label>
                  <select
                    value={reviewData.personId || ''}
                    onChange={(e) =>
                      setReviewData({ ...reviewData, personId: e.target.value })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                  >
                    <option value="">None / Self</option>
                    {people.map((p) => (
                      <option key={p.id} value={p.id}>
                        {language === 'bn' ? p.nameBn : p.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                    {t('fieldDate')}
                  </label>
                  <input
                    type="date"
                    value={reviewData.date}
                    onChange={(e) =>
                      setReviewData({ ...reviewData, date: e.target.value })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                  {t('fieldDescription')}
                </label>
                <input
                  type="text"
                  value={reviewData.description}
                  onChange={(e) =>
                    setReviewData({ ...reviewData, description: e.target.value })
                  }
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                  {t('fieldNote')}
                </label>
                <input
                  type="text"
                  value={reviewData.note}
                  onChange={(e) =>
                    setReviewData({ ...reviewData, note: e.target.value })
                  }
                  className="w-full px-3 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white text-sm"
                />
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2 pt-4 border-t border-slate-100 dark:border-slate-800">
                <button
                  type="button"
                  onClick={() => setReviewData(null)}
                  className="px-4 py-2 rounded-xl border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 text-sm font-medium hover:bg-slate-100 dark:hover:bg-slate-800"
                >
                  আবার বলুন / ভয়েস স্ক্রিনে ফিরুন
                </button>
                <button
                  type="button"
                  onClick={handleSaveConfirmed}
                  className="flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm transition-all shadow-md shadow-emerald-600/20"
                >
                  <Check className="w-4 h-4" />
                  <span>নিশ্চিত করে সংরক্ষণ করুন</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

