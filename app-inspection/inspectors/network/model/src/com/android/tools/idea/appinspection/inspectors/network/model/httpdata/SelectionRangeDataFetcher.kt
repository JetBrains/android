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

/**
 * Listener subscription to [SelectionRangeDataFetcher] that fires every time the range selection is
 * updated.
 */
interface SelectionRangeDataListener {
  fun onUpdate(data: List<HttpData>)
}

/** Listener subscription that only fires when the data in the range selection is changed. */
abstract class SelectionRangeDataChangedListener : SelectionRangeDataListener {
  private var previousData: List<HttpData>? = null

  final override fun onUpdate(data: List<HttpData>) {
    if (previousData != data) {
      previousData = data.also { onDataChanged(it) }
    }
  }

  abstract fun onDataChanged(data: List<HttpData>)
}

/**
 * A class which handles querying of [HttpData] requests based on the [range] selection. When the
 * range changes, the list will automatically be updated, and this class will notify any listeners.
 * When the selection is cleared, listeners will get all [HttpData] and will continue to receive new
 * [HttpData] as they arrive.
 */
class SelectionRangeDataFetcher(
  private val dataModel: HttpDataModel,
  private val selectionRange: Range,
  private val dataRange: Range
) {
  private val aspectObserver = AspectObserver()
  private val listeners = mutableListOf<SelectionRangeDataListener>()

  /**
   * The last list of requests polled from the user's device. Initialized to `null` to distinguish
   * that case from the case where a range returns no requests.
   */
  private var prevDataList: List<HttpData>? = null

  init {
    selectionRange.addDependency(aspectObserver).onChange(Range.Aspect.RANGE) {
      handleRangeUpdated()
    }
    dataRange.addDependency(aspectObserver).onChange(Range.Aspect.RANGE) { handleRangeUpdated() }
    handleRangeUpdated()
  }

  fun addOnChangedListener(callback: (List<HttpData>) -> Unit) {
    addListener(
      object : SelectionRangeDataChangedListener() {
        override fun onDataChanged(data: List<HttpData>) = callback(data)
      }
    )
  }

  fun addListener(listener: SelectionRangeDataListener) {
    listeners.add(listener)
    prevDataList?.let { listener.onUpdate(it) }
  }

  private fun handleRangeUpdated() {
    val dataList = dataModel.getData(if (selectionRange.isEmpty) dataRange else selectionRange)
    prevDataList = dataList.also { fireListeners(it) }
  }

  private fun fireListeners(dataList: List<HttpData>) {
    listeners.forEach { listener -> listener.onUpdate(dataList) }
  }
}
