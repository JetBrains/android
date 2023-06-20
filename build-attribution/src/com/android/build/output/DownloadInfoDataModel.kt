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
package com.android.build.output

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * This class is responsible for processing incoming Gradle download updates in time and in the right order.
 * Updates are coming in build worker thread but should be passed to subscribed models in EDT.
 * The order of scheduled 'invokeLater' can change that's why we need more complex logic using intermediate [updatesQueue]
 * to process updates in the right order. We also try to avoid scheduling too many 'invokeLater' calls in case of EDT lagging behind.
 *
 * On build finished we also schedule last 'process updates' task to process any possible stale updates. No further updates
 * should be coming after this point.
 *
 * It records all the received updates in a list so that it can be replayed to any model subscribed later. This guarantees that no
 * events will be missed by UI.
 */
class DownloadInfoDataModel(
  buildFinishedDisposable: Disposable,
  private val longDownloadsNotifier: LongDownloadsNotifier? = null
) {
  private val updatesQueue = ConcurrentLinkedQueue<DownloadRequestItem>()
  @Volatile private var immediateUpdateScheduled: Boolean = false

  init {
    // On build finished schedule last updates processing task unconditionally
    Disposer.register(buildFinishedDisposable) { invokeLater { processUpdatesQueue() } }
  }

  fun downloadStarted(startTimestamp: Long, url: String, repository: DownloadsAnalyzer.Repository) {
    val requestKey = DownloadRequestKey(startTimestamp, url)
    val requestItem = DownloadRequestItem(requestKey, repository, false, 0, 0, null)
    onNewItemUpdate(requestItem)
  }

  fun downloadFinished(downloadResult: DownloadsAnalyzer.DownloadResult) {
    val requestItem = DownloadRequestItem(
      requestKey = DownloadRequestKey(downloadResult.timestamp, downloadResult.url),
      repository = downloadResult.repository,
      completed = true,
      receivedBytes = downloadResult.bytes,
      duration = downloadResult.duration,
      failureMessage = when (downloadResult.status) {
        DownloadsAnalyzer.DownloadStatus.SUCCESS -> null
        DownloadsAnalyzer.DownloadStatus.MISSED -> "Not Found"
        DownloadsAnalyzer.DownloadStatus.FAILURE -> downloadResult.failureMessage
      }
    )
    onNewItemUpdate(requestItem)
  }

  fun onNewItemUpdate(downloadRequest: DownloadRequestItem) {
    updatesQueue.add(downloadRequest)
    longDownloadsNotifier?.updateDownloadRequest(downloadRequest)
    scheduleImmediateUpdateIfNecessary()
  }

  /**
   * This function guarantees that there will be at least 1 execution of [processUpdatesQueue] after calling this without overwhelming
   * EDT with runnable objects after each new update.
   * - when [immediateUpdateScheduled] is false it means that there is no runnable executions scheduled,
   * though 1 could be executing right now. It makes sense to schedule one more in this case.
   * - when [immediateUpdateScheduled] is true it means that at least 1 runnable was scheduled and did not start execution yet so
   * no need to schedule more.
   */
  private fun scheduleImmediateUpdateIfNecessary() {
    if (!immediateUpdateScheduled) {
      immediateUpdateScheduled = true
      invokeLater {
        immediateUpdateScheduled = false
        processUpdatesQueue()
      }
    }
  }

  /** Should only be accessed from EDT */
  private val processedEvents = mutableListOf<DownloadRequestItem>()
  /** Should only be accessed from EDT */
  private val subscribedModels = mutableListOf<Listener>()

  @UiThread
  fun processUpdatesQueue() {
    while (true) {
      val requestItem = updatesQueue.poll() ?: break
      processedEvents.add(requestItem)
      notifyListenersOnUpdate(requestItem)
    }
  }

  @UiThread
  fun subscribeUiModel(modelListener: Listener) {
    subscribedModels.add(modelListener)
    processedEvents.forEach { modelListener.updateDownloadRequest(it) }
  }

  @UiThread
  fun unsubscribeUiModel(modelListener: Listener) {
    subscribedModels.remove(modelListener)
  }

  @UiThread
  fun notifyListenersOnUpdate(updatedItem: DownloadRequestItem) {
    subscribedModels.forEach { it.updateDownloadRequest(updatedItem) }
  }

  interface Listener {
    fun updateDownloadRequest(downloadRequest: DownloadRequestItem)
  }
}
