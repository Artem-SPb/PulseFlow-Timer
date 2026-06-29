package com.artspb.pulseflowtimer

import android.os.SystemClock
import android.view.View

/**
 * Extension-функция для защиты от спам-кликов (Debounce).
 * 
 * В Android пользователи часто могут случайно (или специально) нажать на кнопку несколько раз подряд
 * за долю секунды. Это может привести к многократным запускам таймера или другим нежелательным
 * побочным эффектам.
 *
 * Решение:
 * Сохраняем время последнего клика в тег View (setTag/getTag).
 * Если с момента прошлого клика прошло меньше времени, чем [delay] миллисекунд, 
 * мы просто игнорируем нажатие.
 */
fun View.setDebouncedListener(delay: Long = 600L, action: () -> Unit) {
    this.setOnClickListener { view ->
        val lastClickTime = view.getTag(R.id.debounce_tag_id) as? Long ?: 0L
        val currentTime = SystemClock.elapsedRealtime()

        if (currentTime - lastClickTime >= delay) {
            view.setTag(R.id.debounce_tag_id, currentTime)
            action()
        }
    }
}
