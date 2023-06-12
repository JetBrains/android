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
package com.android.tools.idea.logcat.util

import com.intellij.ui.AncestorListenerAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.swing.JComponent
import javax.swing.event.AncestorEvent

/**
 * Tracks the visibility of a [JComponent] by creating a [Flow<Boolean>] where each item represents the visibility state
 */
internal fun JComponent.trackVisibility(): Flow<Boolean> {
  return callbackFlow {
    val callback = object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent) {
        trySendBlocking(true)
          .onFailure { LOGGER.warn("Failed to send visibility event", it) }
      }

      override fun ancestorRemoved(event: AncestorEvent) {
        trySendBlocking(false)
          .onFailure { LOGGER.warn("Failed to send visibility event", it) }
      }
    }
    addAncestorListener(callback)
    awaitClose {
      removeAncestorListener(callback)
    }
  }
}
