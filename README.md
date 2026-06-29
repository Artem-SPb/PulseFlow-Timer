[🇬🇧 English](README.md) | [🇷🇺 Русский](README.ru.md)

# PulseFlow: Precision Interval Timer

PulseFlow is a reference pet project of a classic Android timer, written in Kotlin + XML.
The project demonstrates the correct engineering approach to Lifecycle management and 
working with asynchronous tasks (Handler + Runnable).

### UI Preview
![PulseFlow Dashboard](https://repository-images.githubusercontent.com/1284184045/2a7b4080-19a9-4eef-a125-b86512bbf2d7)

## Project Features
* **UI Architecture**: Flat hierarchy using `ConstraintLayout` with `Guideline` and `Barrier`. No nested `LinearLayout`s for maximum rendering performance.
* **Themes**: Full support for Day/Night modes using Material 3 semantic colors (`colors.xml`).
* **UX/UI**: Smooth progress bar, large convenient input elements (`TextInputLayout`), and a dynamic Start/Pause button.
* **Modern Vibration API**: Usage of `VibratorManager` and `VibrationEffect.createOneShot()` for API 26+ with a safe fallback for older versions.

## Engineering Solutions

### 1. Combating Memory Leaks with Handler
One of the most common mistakes made by Junior developers is launching background tasks via `Handler` without properly stopping them when the screen is destroyed.

In `MainActivity`, the timer works through a `Runnable` object that is posted (`postDelayed`) to the main thread's message queue. This anonymous `Runnable` implicitly holds a hard reference to the `MainActivity` instance.

**The Solution:**
In the `onDestroy()` method, we strictly call:
```kotlin
mainThreadHandler?.removeCallbacksAndMessages(null)
```
Without this line, if the user rotates the phone or closes the screen with an active timer, the old `Activity` cannot be collected by the Garbage Collector, because the `MessageQueue` holds the `Runnable`, which in turn holds the old `Activity`. This leads to a classic context leak (Memory Leak).

### 2. Debounce (Protection against spam clicks)
Users often accidentally tap a button multiple times within a fraction of a second. To prevent multiple executions of the logic, a special Extension was written in `ViewExtensions.kt`:
```kotlin
fun View.setDebouncedListener(delay: Long = 600L, action: () -> Unit)
```
It uses `setTag()` to save the time of the last click and ignores subsequent presses within the specified `delay`.

### 3. State Preservation (SURVIVABILITY during Activity recreation)
When the user rotates the phone, the system destroys the old Activity and creates a new one. In `PulseFlow`, the timer does not get reset or freeze.

**How it works:**
1. `onSaveInstanceState` saves the timer status, base duration, and its target completion time (`targetEndTimeMs`).
2. `onRestoreInstanceState` (or `onCreate`) extracts this data.
3. If the timer was active, the system recalculates the remaining time *on the fly*: `timeLeftMs = targetEndTimeMs - SystemClock.elapsedRealtime()`.
4. Thanks to the use of `SystemClock.elapsedRealtime()`, we are not affected by user system time changes, and the timer honestly counts the time while the screen was rotating.

## Author
Developed by [Artem-SPb](https://github.com/Artem-SPb).
