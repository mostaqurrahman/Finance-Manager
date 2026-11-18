package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * In-app AAC/M4A audio recorder wrapping Android MediaRecorder so Bengali voice clips
 * can be captured directly inside the app and transcribed/parsed by Gemini AI Logic
 * without relying solely on external RecognizerIntent / Google Voice Typing.
 */
class AudioVoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun startRecording(): Result<Unit> {
        return try {
            cancelAndRelease()
            val file = File(context.cacheDir, "voice_input_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            isRecording = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AudioVoiceRecorder", "Failed to start audio recording", e)
            cancelAndRelease()
            Result.failure(e)
        }
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        val currentRecorder = recorder
        val file = outputFile
        if (!isRecording || currentRecorder == null || file == null) {
            cancelAndRelease()
            return Result.failure(IllegalStateException("রেকর্ডিং চালু ছিল না।"))
        }

        return try {
            currentRecorder.stop()
            currentRecorder.release()
            recorder = null
            isRecording = false

            val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
            file.delete()
            outputFile = null

            if (bytes.isEmpty()) {
                Result.failure(IllegalStateException("অডিও খালি ছিল। মাইক্রোফোনের কাছে পরিষ্কারভাবে বাংলায় বলুন।"))
            } else {
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Log.e("AudioVoiceRecorder", "Failed to stop audio recording", e)
            cancelAndRelease()
            Result.failure(Exception("অডিও রেকর্ডিং সংরক্ষণ করা যায়নি। অনুগ্রহ করে একটু বড় করে বলুন বা নিচে টাইপ করুন।"))
        }
    }

    fun cancelAndRelease() {
        try {
            if (isRecording) {
                recorder?.stop()
            }
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        try {
            outputFile?.delete()
        } catch (_: Exception) {
        }
        outputFile = null
    }
}
