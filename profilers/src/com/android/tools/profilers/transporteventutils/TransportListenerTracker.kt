/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.transporteventutils

import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.profilers.StudioProfilers
import java.util.concurrent.TimeUnit

private const val TIMEOUT_SECONDS = 10L

/**
 * Helper class to track and unregister TransportEventListeners to prevent memory leaks. Supports deferred unregistration for stop listeners
 * to allow operations to complete.
 */
class TransportListenerTracker(private val profilers: StudioProfilers) {
  private val listenersLock = Any()
  private var isExited = false
  private val activeListeners = mutableListOf<TransportEventListener>()
  private val survivingListeners = mutableListOf<TransportEventListener>()

  /**
   * Tracks a [TransportEventListener] to ensure it is unregistered when the stage exits.
   *
   * @param listener The listener to track.
   * @param surviveExitWithTimeout If true, the listener will survive stage exit for [TIMEOUT_SECONDS] seconds before being unregistered.
   */
  fun trackListener(listener: TransportEventListener, surviveExitWithTimeout: Boolean) {
    synchronized(listenersLock) {
      if (isExited) {
        val poller = profilers.transportPoller
        if (surviveExitWithTimeout) {
          registerDelayedUnregister(poller, listener)
        } else {
          poller.unregisterListener(listener)
        }
      } else {
        if (surviveExitWithTimeout) {
          survivingListeners.add(listener)
        } else {
          activeListeners.add(listener)
        }
      }
    }
  }

  /**
   * Called when the stage exits. Unregisters all active listeners immediately and schedules surviving listeners to be unregistered after a
   * [TIMEOUT_SECONDS]-second timeout.
   */
  fun onExit() {
    synchronized(listenersLock) {
      isExited = true
      val poller = profilers.transportPoller
      activeListeners.forEach { poller.unregisterListener(it) }
      activeListeners.clear()

      survivingListeners.forEach { listener -> registerDelayedUnregister(poller, listener) }
      survivingListeners.clear()
    }
  }

  /** Registers a one-off timer with the global updater to unregister the listener after [TIMEOUT_SECONDS] seconds. */
  private fun registerDelayedUnregister(poller: TransportEventPoller, listener: TransportEventListener) {
    profilers.updater.register(
      object : Updatable {
        var elapsedNs = 0L
        val timeoutNs = TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)

        override fun update(elapsedNs: Long) {
          this.elapsedNs += elapsedNs
          if (this.elapsedNs >= timeoutNs) {
            poller.unregisterListener(listener)
            profilers.updater.unregister(this)
          }
        }
      }
    )
  }
}
