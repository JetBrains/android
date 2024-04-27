/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.diagnostics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.modes.essentials.EssentialsMode
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.SystemHealthEvent
import com.google.wireless.android.sdk.stats.SystemHealthEvent.DeadlockStatus
import com.google.wireless.android.sdk.stats.SystemHealthEvent.LowMemoryWarningType
import com.intellij.diagnostic.IdePerformanceListener
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.VisibleForTesting
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.time.Duration
import java.util.Arrays
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.regex.Pattern

private const val MAX_GC_EVENTS = 2_000

@Service(Service.Level.APP)
class SystemHealthDataCollection: Disposable {
  private val freezeCounter = AtomicInteger(0)
  private var deadlockStatus = DeadlockStatus.UNKNOWN
  private val freezeHeartbeat = FreezeHeartbeat { durationMs: Long -> freezeHeartbeat(durationMs) }

  @VisibleForTesting
  var clock = Clock { System.nanoTime() / 1_000_000 }

  @VisibleForTesting
  var dedicatedThreadExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("SystemHealthDataCollection", 1)

  val triggers = Triggers()

  fun start() {
    setUpFreezeTelemetry()
    setUpExitTelemetry()
    setUpMemoryTelemetry()
  }

  inner class Triggers {
    private val gcEventsCounter = AtomicInteger(0)
    fun gcThresholdMet() {
      if (gcEventsCounter.incrementAndGet() <= MAX_GC_EVENTS) {
        logLowMemoryWarning(LowMemoryWatcher.LowMemoryWatcherType.ALWAYS)
      }
    }

    fun gcThresholdMetAfterCollection() {
      if (gcEventsCounter.incrementAndGet() <= MAX_GC_EVENTS) {
        logLowMemoryWarning(LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)
      }
    }

    fun outOfMemoryErrorRaised() {
      logSystemHealthEvent(
        SystemHealthEvent.newBuilder()
          .setEventType(SystemHealthEvent.SystemHealthEventType.MEMORY_OOM_ERROR)
      )
    }

    fun jvmCrashDetected(sessionID: String, signalName: String) {
      logSystemHealthEvent(
        SystemHealthEvent.newBuilder()
          .setEventType(SystemHealthEvent.SystemHealthEventType.EXIT_JVM_CRASH)
          .setExit(
            SystemHealthEvent.Exit.newBuilder()
              .setStudioSessionId(sessionID)
              .setJvmSignalNumber(getSignalNumber(signalName))
          )
      )
    }

    fun gracefulExitDetected(sessionId: String) {
      logSystemHealthEvent(
        SystemHealthEvent.newBuilder()
          .setEventType(SystemHealthEvent.SystemHealthEventType.EXIT_GRACEFUL)
          .setExit(
            SystemHealthEvent.Exit.newBuilder()
              .setStudioSessionId(sessionId)
          )
      )
    }

    fun nongracefulExitDetected(sessionId: String) {
      logSystemHealthEvent(
        SystemHealthEvent.newBuilder()
          .setEventType(SystemHealthEvent.SystemHealthEventType.EXIT_NONGRACEFUL)
          .setExit(
            SystemHealthEvent.Exit.newBuilder()
              .setStudioSessionId(sessionId)
          )
      )
    }

    fun uiFreezeStarted() {
      val unresponsiveInterval = PerformanceWatcher.getInstance().unresponsiveInterval
      val freezeStartTime = clock.milliTime() - unresponsiveInterval
      dedicatedThreadExecutor.submit { freezeStarted(unresponsiveInterval.toLong(), freezeStartTime) }
    }

    fun uiFreezeFinished(durationMs: Long) {
      dedicatedThreadExecutor.submit { freezeFinished(durationMs) }
    }
  }

  private fun setUpMemoryTelemetry() {
    // Low memory notifications
    LowMemoryWatcher.register(
      triggers::gcThresholdMet,
      LowMemoryWatcher.LowMemoryWatcherType.ALWAYS, this)
    LowMemoryWatcher.register(
      triggers::gcThresholdMetAfterCollection,
      LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC, this)

    AndroidStudioSystemHealthMonitor.getInstance()
      ?.registerOutOfMemoryErrorListener({ triggers.outOfMemoryErrorRaised() }, this)
  }

  private fun logLowMemoryWarning(type: LowMemoryWatcher.LowMemoryWatcherType) {
    logSystemHealthEvent(
      SystemHealthEvent.newBuilder()
        .setEventType(SystemHealthEvent.SystemHealthEventType.MEMORY_LOW_MEMORY_WARNING)
        .setMemory(
          SystemHealthEvent.Memory.newBuilder()
            .setLowMemoryWarningType(getLowMemoryWarningType(type))
        )
    )
  }

  private fun getLowMemoryWarningType(type: LowMemoryWatcher.LowMemoryWatcherType): LowMemoryWarningType {
    return when (type) {
      LowMemoryWatcher.LowMemoryWatcherType.ALWAYS -> LowMemoryWarningType.BEFORE_GC
      LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC -> LowMemoryWarningType.AFTER_GC
      else -> LowMemoryWarningType.UNKNOWN_TYPE
    }
  }

  private fun setUpExitTelemetry() {
    val application = ApplicationManager.getApplication()
    val crashDetails = StudioCrashDetection.reapCrashDescriptions()
    for (details in crashDetails) {
      if (details.isJvmCrash) {
        triggers.jvmCrashDetected(details.sessionID, details.errorSignal)
      } else {
        triggers.nongracefulExitDetected(details.sessionID)
      }
    }
    application.messageBus
      .connect(this)
      .subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appClosing() {
          triggers.gracefulExitDetected(UsageTracker.sessionId)
        }
      })
  }

  private fun getSignalNumber(signalName: String): Int {
    val matcher = Pattern.compile("^SIG([A-Z]+).*").matcher(signalName)
    if (matcher.matches()) {
      val extractedSignalName = matcher.group(1)
      val signalMap = createSignalMap()
      return signalMap[extractedSignalName] ?: UNKNOWN_SIGNAL
    }
    return INVALID_SIGNAL
  }

  private fun setUpFreezeTelemetry() {
    val application = ApplicationManager.getApplication()
    application.messageBus.connect(this).subscribe(IdePerformanceListener.TOPIC, object : IdePerformanceListener {
      override fun uiFreezeStarted(reportDir: Path) {
        triggers.uiFreezeStarted()
      }

      override fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {
        triggers.uiFreezeFinished(durationMs)
      }
    })
  }

  fun interface Clock {
    fun milliTime(): Long
  }

  private fun freezeStarted(durationMs: Long, freezeStartTime: Long) {
    val freezeID = freezeCounter.incrementAndGet()
    deadlockStatus = DeadlockStatus.UNKNOWN
    updateDeadlockStatus(durationMs)
    logSystemHealthEvent(
      SystemHealthEvent.newBuilder()
        .setEventType(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_STARTED)
        .setUiFreeze(
          SystemHealthEvent.UIFreeze.newBuilder()
            .setFreezeId(freezeID.toLong())
            .setDeadlock(deadlockStatus)
            .setDurationMs(durationMs)
        )
    )
    freezeHeartbeat.start(freezeStartTime)
  }

  private fun freezeHeartbeat(durationMs: Long) {
    updateDeadlockStatus(durationMs)
    logSystemHealthEvent(
      SystemHealthEvent.newBuilder()
        .setEventType(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_UPDATE)
        .setUiFreeze(
          SystemHealthEvent.UIFreeze.newBuilder()
            .setFreezeId(freezeCounter.get().toLong())
            .setDeadlock(deadlockStatus)
            .setDurationMs(durationMs)
        )
    )
  }

  private fun freezeFinished(durationMs: Long) {
    deadlockStatus = DeadlockStatus.NO_DEADLOCK
    freezeHeartbeat.stop()
    logSystemHealthEvent(
      SystemHealthEvent.newBuilder()
        .setEventType(SystemHealthEvent.SystemHealthEventType.UI_FREEZE_FINISHED)
        .setUiFreeze(
          SystemHealthEvent.UIFreeze.newBuilder()
            .setFreezeId(freezeCounter.get().toLong())
            .setDeadlock(deadlockStatus)
            .setDurationMs(durationMs)
        )
    )
  }

  private fun updateDeadlockStatus(freezeTimeMs: Long) {
    // No need to update status once deadlock has been confirmed
    if (deadlockStatus == DeadlockStatus.CONFIRMED) {
      return
    }

    // Freezes longer than 90 seconds are suspected to have a deadlock
    if (deadlockStatus == DeadlockStatus.UNKNOWN &&
      freezeTimeMs >= TimeUnit.SECONDS.toMillis(90)
    ) {
      deadlockStatus = DeadlockStatus.SUSPECTED
    }
    val bean = ManagementFactory.getThreadMXBean()
    val threadIds = bean.findMonitorDeadlockedThreads()
    if (threadIds != null && threadIds.isNotEmpty()) {
      val edtId = EDT.getEventDispatchThread().id
      val edtBlocked = Arrays.stream(threadIds).anyMatch { tid: Long -> tid == edtId }

      // If EDT is not in a deadlock, but a deadlock is present then set status to suspected
      deadlockStatus = if (edtBlocked) DeadlockStatus.CONFIRMED else DeadlockStatus.SUSPECTED
    }
  }

  override fun dispose() {
  }

  @JvmRecord
  private data class FreezeHeartbeatDelay(val duration: Duration, val interval: Duration)
  private inner class FreezeHeartbeat(private val callback: Consumer<Long>) {
    private var isRunning = false
    private var freezeStart: Long = 0
    private var future: ScheduledFuture<*>? = null
    private var delayIterator = HEARTBEAT_DELAYS.iterator()
    private var currentDelay: FreezeHeartbeatDelay? = null
    fun start(freezeStart: Long) {
      if (isRunning) {
        stop()
      }
      this.freezeStart = freezeStart
      isRunning = true
      currentDelay = null
      delayIterator = HEARTBEAT_DELAYS.iterator()
      scheduleNextHeartbeat()
    }

    private fun scheduleNextHeartbeat() {
      advanceDelayIterator()?.let { heartbeatDelay ->
        future = dedicatedThreadExecutor.schedule({ freezeHeartbeat() }, heartbeatDelay.interval.toMillis(), TimeUnit.MILLISECONDS)
      }
    }

    private fun freezeHeartbeat() {
      future = null
      if (!isRunning) {
        return
      }
      callback.accept(clock.milliTime() - freezeStart)
      scheduleNextHeartbeat()
    }

    private fun advanceDelayIterator(): FreezeHeartbeatDelay? {
      if (currentDelay == null) {
         if (delayIterator.hasNext()) {
          currentDelay = delayIterator.next()
        } else {
          return null
        }
      }
      val currentFreezeTimeMs = clock.milliTime() - freezeStart
      while (currentDelay != null && currentFreezeTimeMs >= currentDelay!!.duration.toMillis()) {
        if (!delayIterator.hasNext()) {
          currentDelay = null
          break
        }
        currentDelay = delayIterator.next()
      }
      return currentDelay
    }

    fun stop() {
      if (!isRunning) {
        return
      }
      if (future != null) {
        future!!.cancel(false)
        future = null
      }
      isRunning = false
    }
  }

  companion object {
    private val HEARTBEAT_DELAYS = listOf(
      FreezeHeartbeatDelay(Duration.ofSeconds(10), Duration.ofSeconds(5)),
      FreezeHeartbeatDelay(Duration.ofMinutes(1), Duration.ofSeconds(10)),
      FreezeHeartbeatDelay(Duration.ofMinutes(5), Duration.ofMinutes(1)),
      FreezeHeartbeatDelay(Duration.ofMinutes(30), Duration.ofMinutes(5)),
      FreezeHeartbeatDelay(Duration.ofHours(6), Duration.ofMinutes(30))
    )
    const val INVALID_SIGNAL = -1
    const val UNKNOWN_SIGNAL = -2
    @JvmStatic
    val instance: SystemHealthDataCollection
      get() = ApplicationManager.getApplication().getService(
        SystemHealthDataCollection::class.java
      )

    private fun createSignalMap(): Map<String, Int> =
      mapOf(
        "HUP" to 1,
        "INT" to 2,
        "QUIT" to 3,
        "ILL" to 4,
        "TRAP" to 5,
        "ABRT" to 6,
        "IOT" to 7,
        "BUS" to 8,
        "KILL" to 9,
        "USR1" to 10,
        "SEGV" to 11,
        "USR2" to 12,
        "PIPE" to 13,
        "ALRM" to 14,
        "TERM" to 15,
        "STKFLT" to 16,
        "CHLD" to 17,
        "CONT" to 18,
        "STOP" to 19,
        "TSTP" to 20,
        "TTIN" to 21,
        "TTOU" to 22,
        "URG" to 23,
        "XCPU" to 24,
        "XFSZ" to 25,
        "VTALRM" to 26,
        "PROF" to 27,
        "WINCH" to 28,
        "IO" to 29,
        "POLL" to 29,
        "PWR" to 30,
        "SYS" to 31,
        "UNUSED" to 31
      )
    }

  private fun logSystemHealthEvent(systemHealthEvent: SystemHealthEvent.Builder) {
    systemHealthEvent.setEssentialsMode(EssentialsMode.isEnabled())
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.SYSTEM_HEALTH_EVENT)
        .setSystemHealthEvent(systemHealthEvent)
    )
  }
}