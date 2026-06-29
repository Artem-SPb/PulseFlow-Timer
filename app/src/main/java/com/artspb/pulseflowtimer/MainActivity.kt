package com.artspb.pulseflowtimer

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var etMinutes: TextInputEditText
    private lateinit var etSeconds: TextInputEditText
    private lateinit var tvTimer: TextView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var btnStartPause: MaterialButton
    private lateinit var btnReset: MaterialButton

    // Handler для обновления UI из главного потока
    private var mainThreadHandler: Handler? = null

    // Состояние таймера
    private var isRunning = false
    private var baseDurationMs = 0L
    private var timeLeftMs = 0L
    private var targetEndTimeMs = 0L

    // Константы для сохранения состояния
    companion object {
        private const val KEY_IS_RUNNING = "KEY_IS_RUNNING"
        private const val KEY_BASE_DURATION_MS = "KEY_BASE_DURATION_MS"
        private const val KEY_TIME_LEFT_MS = "KEY_TIME_LEFT_MS"
        private const val KEY_TARGET_END_TIME_MS = "KEY_TARGET_END_TIME_MS"
    }

    // Runnable, который будет вызываться каждую секунду (или чаще для плавности)
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val currentTime = SystemClock.elapsedRealtime()
            timeLeftMs = targetEndTimeMs - currentTime

            if (timeLeftMs <= 0) {
                // Таймер завершился
                timeLeftMs = 0
                finishTimer()
            } else {
                // Обновляем UI и планируем следующий вызов
                updateTimerUI()
                // Вызываем каждые 50 мс для плавности прогресс-бара
                mainThreadHandler?.postDelayed(this, 50L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        // Инициализируем Handler, привязанный к главному потоку (Looper.getMainLooper())
        // Это безопасно для обновления UI.
        mainThreadHandler = Handler(Looper.getMainLooper())

        // Восстанавливаем состояние, если Activity была пересоздана (например, при повороте экрана)
        if (savedInstanceState != null) {
            restoreTimerState(savedInstanceState)
        }

        setupListeners()
        updateTimerUI()
        updateButtonsUI()
    }

    private fun initViews() {
        etMinutes = findViewById(R.id.et_minutes)
        etSeconds = findViewById(R.id.et_seconds)
        tvTimer = findViewById(R.id.tv_timer)
        progressIndicator = findViewById(R.id.progress_indicator)
        btnStartPause = findViewById(R.id.btn_start_pause)
        btnReset = findViewById(R.id.btn_reset)
    }

    private fun setupListeners() {
        // Использую написанный extension для защиты от спам-кликов. 
        // Теперь если юзер будет бешено кликать на кнопку, сработает только один клик раз в 600мс.
        btnStartPause.setDebouncedListener {
            hideKeyboard()
            if (isRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        btnReset.setDebouncedListener {
            hideKeyboard()
            resetTimer()
        }
    }

    private fun startTimer() {
        if (timeLeftMs == 0L) {
            // Если таймер запускается с нуля, считываем значения из полей ввода
            val minutesStr = etMinutes.text?.toString() ?: ""
            val secondsStr = etSeconds.text?.toString() ?: ""

            val minutes = minutesStr.toLongOrNull() ?: 0L
            val seconds = secondsStr.toLongOrNull() ?: 0L

            baseDurationMs = (minutes * 60 + seconds) * 1000L
            timeLeftMs = baseDurationMs
        }

        if (timeLeftMs == 0L) {
            // Если юзер ничего не ввел, не запускаем
            return
        }

        isRunning = true
        targetEndTimeMs = SystemClock.elapsedRealtime() + timeLeftMs
        
        updateButtonsUI()
        
        // Запускаем Runnable через Handler
        mainThreadHandler?.post(timerRunnable)
    }

    private fun pauseTimer() {
        isRunning = false
        // Убираем коллбэки, чтобы таймер перестал обновляться в фоне
        mainThreadHandler?.removeCallbacks(timerRunnable)
        updateButtonsUI()
    }

    private fun resetTimer() {
        isRunning = false
        mainThreadHandler?.removeCallbacks(timerRunnable)
        
        baseDurationMs = 0L
        timeLeftMs = 0L
        targetEndTimeMs = 0L
        
        etMinutes.text?.clear()
        etSeconds.text?.clear()
        
        updateTimerUI()
        updateButtonsUI()
    }

    private fun finishTimer() {
        isRunning = false
        updateTimerUI()
        updateButtonsUI()
        triggerVibration()
    }

    private fun updateTimerUI() {
        // Высчитываем минуты и секунды для текста
        val totalSecondsLeft = (timeLeftMs / 1000).toInt()
        val minutes = totalSecondsLeft / 60
        val seconds = totalSecondsLeft % 60
        tvTimer.text = String.format("%02d:%02d", minutes, seconds)

        // Обновляем прогресс-бар
        if (baseDurationMs > 0) {
            // max = 1000, поэтому считаем долю
            val progress = ((timeLeftMs.toFloat() / baseDurationMs.toFloat()) * 1000).toInt()
            progressIndicator.progress = progress
        } else {
            progressIndicator.progress = 1000
        }
    }

    private fun updateButtonsUI() {
        if (isRunning) {
            btnStartPause.setText(R.string.action_pause)
            btnStartPause.setBackgroundColor(ContextCompat.getColor(this, R.color.color_pause))
        } else {
            if (timeLeftMs > 0 && timeLeftMs < baseDurationMs) {
                btnStartPause.setText(R.string.action_resume)
            } else {
                btnStartPause.setText(R.string.action_start)
            }
            btnStartPause.setBackgroundColor(ContextCompat.getColor(this, R.color.color_start))
        }
    }

    // Современный вызов вибрации (API 26+) с безопасным фоллбэком для старых версий
    @Suppress("DEPRECATION")
    private fun triggerVibration() {
        val vibrationDuration = 1000L // 1 секунда
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(vibrationDuration)
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Если таймер работает, мы должны обновить timeLeftMs на момент сохранения
        if (isRunning) {
            timeLeftMs = targetEndTimeMs - SystemClock.elapsedRealtime()
        }
        
        outState.putBoolean(KEY_IS_RUNNING, isRunning)
        outState.putLong(KEY_BASE_DURATION_MS, baseDurationMs)
        outState.putLong(KEY_TIME_LEFT_MS, timeLeftMs)
        outState.putLong(KEY_TARGET_END_TIME_MS, targetEndTimeMs)
    }

    private fun restoreTimerState(savedInstanceState: Bundle) {
        isRunning = savedInstanceState.getBoolean(KEY_IS_RUNNING, false)
        baseDurationMs = savedInstanceState.getLong(KEY_BASE_DURATION_MS, 0L)
        timeLeftMs = savedInstanceState.getLong(KEY_TIME_LEFT_MS, 0L)
        targetEndTimeMs = savedInstanceState.getLong(KEY_TARGET_END_TIME_MS, 0L)

        if (isRunning) {
            // Если таймер должен работать, пересчитываем оставшееся время
            // Это нужно, чтобы при повороте экрана таймер не "замирал", а честно учитывал прошедшее время
            val currentTime = SystemClock.elapsedRealtime()
            timeLeftMs = targetEndTimeMs - currentTime
            
            if (timeLeftMs > 0) {
                // Возобновляем работу таймера
                mainThreadHandler?.post(timerRunnable)
            } else {
                // Если время вышло, пока мы крутили экран
                timeLeftMs = 0
                finishTimer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        /* 
         * [Борьба с Memory Leaks]
         * Вызываю removeCallbacksAndMessages(null), так как timerRunnable неявно держит ссылку
         * на Activity (является анонимным классом внутри MainActivity). 
         * Если юзер перевернет телефон или закроет экран, когда таймер запущен, 
         * Handler, который привязан к главному потоку, продолжит хранить этот Runnable 
         * в своей очереди сообщений (MessageQueue).
         * Из-за этого Garbage Collector не сможет очистить старую Activity из памяти, 
         * и произойдет утечка контекста (Memory Leak).
         * Очистка очереди сообщений гарантирует, что никаких отложенных вызовов
         * не останется, и Activity сможет быть успешно уничтожена сборщиком мусора.
         */
        mainThreadHandler?.removeCallbacksAndMessages(null)
        mainThreadHandler = null
    }
}