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
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** A display only action for showing timestamp for issues fetch/refresh from last. */
class AppInsightsDisplayRefreshTimestampAction(
  timestamp: Flow<Timestamp>,
  private val clock: Clock,
  scope: CoroutineScope
) : AnAction(), CustomComponentAction {
  private val lastModifiedTimestamp: AtomicReference<Timestamp> =
    AtomicReference(Timestamp.UNINITIALIZED)
  private val label =
    JBLabel().apply {
      text = displayText
      isEnabled = false
      border = JBUI.Borders.empty(0, 5, 0, 5)
    }

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
        label.text = displayText
      }
    }

    scope.launch {
      // Force updating display text every minute.
      while (true) {
        label.text = displayText
        delay(UPDATE_INTERVAL_IN_MILLIS)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) = Unit

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return label
  }

  companion object {
    private val UPDATE_INTERVAL_IN_MILLIS = Duration.ofMinutes(1).toMillis()
  }
}
