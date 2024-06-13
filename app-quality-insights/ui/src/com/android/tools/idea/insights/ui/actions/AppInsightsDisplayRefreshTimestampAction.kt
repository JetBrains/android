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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.idea.insights.ui.Timestamp
import com.android.tools.idea.insights.ui.TimestampState
import com.android.tools.idea.insights.ui.offlineModeIcon
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A display only action for showing timestamp for issues fetch/refresh from last. */
class AppInsightsDisplayRefreshTimestampAction(
  timestamp: Flow<Timestamp>,
  private val clock: Clock,
  scope: CoroutineScope,
) : AnAction() {
  private val lastModifiedTimestamp: AtomicReference<Timestamp> =
    AtomicReference(Timestamp.UNINITIALIZED)

  @VisibleForTesting
  val displayText: String
    get() = lastModifiedTimestamp.get().toDisplayString(clock.millis())

  init {
    scope.launch {
      timestamp.collect { time: Timestamp ->
        lastModifiedTimestamp.getAndUpdate { prev ->
          // Carry over last timestamp.
          Timestamp(time.time ?: prev.time, time.state)
        }

        // Force updating display text after timestamp update (i.e. loading state change).
        ActivityTracker.getInstance().inc()
      }
    }

    scope.launch {
      // Force updating display text every minute.
      while (true) {
        ActivityTracker.getInstance().inc()
        delay(UPDATE_INTERVAL_IN_MILLIS)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.apply {
      isEnabled = false
      text = displayText
      disabledIcon =
        if (lastModifiedTimestamp.get().state == TimestampState.OFFLINE) {
          offlineModeIcon
        } else {
          null
        }
    }
  }

  override fun actionPerformed(e: AnActionEvent) = Unit

  override fun displayTextInToolbar() = true

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  companion object {
    private val UPDATE_INTERVAL_IN_MILLIS = Duration.ofMinutes(1).toMillis()
  }
}
