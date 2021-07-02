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
package com.android.tools.idea.appinspection.inspectors.network.model.httpdata

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range

typealias HttpDataFetcherListener = (List<HttpData>) -> Unit

/**
 * Allows for subscribing to receive all [HttpData] that fall within a selection range.
 * When the selection is cleared, listeners will get all [HttpData] and will continue
 * to receive new [HttpData] as they arrive.
 */
class HttpDataFetcher(private val dataModel: HttpDataModel, private val selectionRange: Range, private val dataRange: Range) {
  private val aspectObserver = AspectObserver()
  private val listeners = mutableListOf<HttpDataFetcherListener>()

  /**
   * The last list of requests polled from the user's device. Initialized to `null` to
   * distinguish that case from the case where a range returns no requests.
   */
  private var prevDataList: List<HttpData>? = null

  init {
    selectionRange.addDependency(aspectObserver).onChange(Range.Aspect.RANGE) { handleRangeUpdated() }
    dataRange.addDependency(aspectObserver).onChange(Range.Aspect.RANGE) { handleRangeUpdated() }
    handleRangeUpdated()
  }

  fun addListener(listener: HttpDataFetcherListener) {
    listeners.add(listener)
    prevDataList?.let { listener.invoke(it) }
  }

  private fun handleRangeUpdated() {
    val dataList = if (!selectionRange.isEmpty) dataModel.getData(selectionRange) else dataModel.getData(dataRange)
    if (prevDataList != null && dataList.size == prevDataList?.size) {
      return
    }
    prevDataList = dataList.also { fireListeners(it) }
  }

  private fun fireListeners(dataList: List<HttpData>) {
    listeners.forEach { it(dataList) }
  }
}