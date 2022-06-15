/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.util.ListenerCollection
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@VisibleForTesting val CONNECTED_STATE = AttachErrorState.MODEL_UPDATED
@VisibleForTesting const val CONNECT_TIMEOUT_SECONDS: Long = 30L

class InspectorClientLaunchMonitor(
  private val attachErrorStateListeners: ListenerCollection<(AttachErrorState) -> Unit>,
  @TestOnly private val executorService: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService()
) {
  private var lastUpdate: Long = 0L
  private var currentProgress = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
  private var currentFuture: ScheduledFuture<*>? = null
  private var client: InspectorClient? = null

  fun start(client: InspectorClient) {
    assert(this.client == null)
    this.client = client
    updateProgress(AttachErrorState.NOT_STARTED)
  }

  fun updateProgress(progress: AttachErrorState) {
    attachErrorStateListeners.forEach { it.invoke(progress) }

    if (progress <= currentProgress) {
      return
    }
    currentFuture?.cancel(true)
    currentProgress = progress
    if (currentProgress < CONNECTED_STATE) {
      lastUpdate = System.currentTimeMillis()
      currentFuture = executorService.schedule(
        {
          client?.let { client ->
            Logger.getInstance(InspectorClientLaunchMonitor::class.java).warn(
              "Client $client timed out during attach at step $currentProgress")
            logAttachError(AttachErrorCode.CONNECT_TIMEOUT)
            client.disconnect()
          }
        },
        CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS
      )
    }
  }

  fun onFailure(t: Throwable) {
    logAttachError(t.errorCode)
    stop()
  }

  private fun logAttachError(errorCode: AttachErrorCode) {
    val stats = client?.stats ?: DisconnectedClient.stats
    LayoutInspectorMetrics(null, client?.process, null).logEvent(
      DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_ERROR, stats, currentProgress, errorCode)
  }

  fun stop() {
    currentFuture?.cancel(true)
    currentFuture = null
    client = null
  }
}