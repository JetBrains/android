/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.instrumentation.threading

import com.android.tools.analytics.UsageTracker
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerHook
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.swing.SwingUtilities

// Send the usage report only if more than 60 seconds have passed since the last report
private const val REPORT_FREQUENCY_MS = 60_000L

/** Connects to the threading java agent from Android Studio. */
class ThreadingCheckerHookImpl(
  private val threadingViolationNotifier: ThreadingViolationNotifier =
    ThreadingViolationNotifierImpl()
) : ThreadingCheckerHook {

  private val logger = thisLogger()
  private val lock = Any()

  @Volatile
  private var lastReportedUsageTimeMs = 0L

  // These counters are not precise, but they are good enough for being included in the usage metrics.
  private var uiThreadCheckCount = 0L
  private var workerThreadCheckCount = 0L

  @VisibleForTesting
  val threadingViolations: ConcurrentMap<String, AtomicLong> = ConcurrentHashMap()

  override fun verifyOnUiThread() {
    ++uiThreadCheckCount
    maybeReportUsageStats()

    if (SwingUtilities.isEventDispatchThread()) {
      return
    }
    recordViolation("Threading violation: methods annotated with @UiThread should be called on the UI thread")
  }

  override fun verifyOnWorkerThread() {
    ++workerThreadCheckCount
    maybeReportUsageStats()

    if (!SwingUtilities.isEventDispatchThread()) {
      return
    }
    recordViolation("Threading violation: methods annotated with @WorkerThread should not be called on the UI thread")
  }

  private fun maybeReportUsageStats() {
    val currentTimeMs = System.currentTimeMillis()
    if (currentTimeMs - lastReportedUsageTimeMs < REPORT_FREQUENCY_MS) {
      return
    }

    synchronized(lock) {
      if (currentTimeMs - lastReportedUsageTimeMs < REPORT_FREQUENCY_MS) {
        return
      }
      lastReportedUsageTimeMs = currentTimeMs
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.THREADING_AGENT_STATS)
        .setThreadingAgentUsageEvent(
          ThreadingAgentUsageEvent.newBuilder()
            .setVerifyUiThreadCount(uiThreadCheckCount)
            .setVerifyWorkerThreadCount(workerThreadCheckCount))
    )
    uiThreadCheckCount = 0
    workerThreadCheckCount = 0
  }

  private fun recordViolation(warningMessage: String) {
    val stackTrace = Thread.currentThread().stackTrace
    // Index of an annotated method. We need to skip Thread#getStackTrace, ThreadingCheckerHookImpl#recordViolation,
    // ThreadingCheckerHookImpl#verifyOnUiThread, ThreadingCheckerTrampoline#verifyOnUiThread stack frames.
    val annotatedMethodIndex = 4
    val methodSignature =
      stackTrace[annotatedMethodIndex].className + "#" + stackTrace[annotatedMethodIndex].methodName
    val violationCount = threadingViolations.computeIfAbsent(methodSignature) { AtomicLong() }.incrementAndGet()
    val loggedStackTrace = Stream.of(*stackTrace).skip(annotatedMethodIndex.toLong()).map { it.toString() }
      .collect(Collectors.joining("\n  "))
    val message = "$warningMessage\nViolating method: $methodSignature\nStack trace:\n$loggedStackTrace"
    if (shouldLogErrors()) {
      logger.error(message)
    }
    else {
      logger.warn(message)
    }

    // Only show one notification per method signature
    if (violationCount == 1L && !shouldSuppressNotifications()) {
      threadingViolationNotifier.notify(warningMessage, methodSignature)
    }
  }

  private fun shouldLogErrors(): Boolean {
    return System.getProperty("android.studio.instrumentation.threading.log-errors", "true")
      .equals("true", ignoreCase = true)
  }

  private fun shouldSuppressNotifications(): Boolean {
    return System.getProperty("android.studio.instrumentation.threading.suppress-notifications", "true")
      .equals("true", ignoreCase = true)
  }
}
