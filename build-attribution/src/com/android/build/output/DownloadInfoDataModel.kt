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

/**
 * This class is responsible for processing incoming Gradle download updates in time and in the right order.
 * Updates are coming in build worker thread but should be passed to subscribed models in EDT.
 * The order of scheduled 'invokeLater' can change that's why we need more complex logic using intermediate [updatesMap]
 * to process updates in the right order. [updatesMap] holds latest received request data by it's key.
 * We also try to avoid scheduling too many 'invokeLater' calls in case of EDT lagging behind.
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
  private val updatesMap = mutableMapOf<DownloadRequestKey, DownloadRequestItem>()
  @Volatile private var immediateUpdateScheduled: Boolean = false

  init {
    // On build finished schedule last updates processing task unconditionally
    Disposer.register(buildFinishedDisposable) { invokeLater { processUpdates() } }
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
    synchronized(updatesMap) { updatesMap[downloadRequest.requestKey] = downloadRequest }
    longDownloadsNotifier?.updateDownloadRequest(downloadRequest)
    scheduleImmediateUpdateIfNecessary()
  }

  /**
   * This function guarantees that there will be at least 1 execution of [processUpdates] after calling this without overwhelming
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
        processUpdates()
      }
    }
  }

  /** Should only be accessed from EDT */
  private val processedEvents = mutableMapOf<DownloadRequestKey, DownloadRequestItem>()
  /** Should only be accessed from EDT */
  private val subscribedModels = mutableListOf<Listener>()

  @UiThread
  fun processUpdates() {
    val newUpdates = synchronized(updatesMap) {
      ArrayList(updatesMap.values).also {
        updatesMap.clear()
      }
    }


    for (requestItem in newUpdates) {
      processedEvents[requestItem.requestKey] = requestItem
    }
    notifyListenersOnUpdate(newUpdates)
  }

  @UiThread
  fun subscribeUiModel(modelListener: Listener) {
    subscribedModels.add(modelListener)
    modelListener.updateDownloadRequests(processedEvents.values.toList())
  }

  @UiThread
  fun unsubscribeUiModel(modelListener: Listener) {
    subscribedModels.remove(modelListener)
  }

  @UiThread
  fun notifyListenersOnUpdate(updatedItems: List<DownloadRequestItem>) {
    subscribedModels.forEach { it.updateDownloadRequests(updatedItems) }
  }

  interface Listener {
    fun updateDownloadRequests(downloadRequests: List<DownloadRequestItem>)
  }
}
