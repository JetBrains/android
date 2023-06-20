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

import com.android.tools.adtui.model.Range
import java.util.concurrent.CountDownLatch
import junit.framework.Assert.fail
import org.junit.Test

class SelectionRangeDataFetcherTest {

  @Test
  fun listenerFiresOnSelectionRangeChange() {
    val dataModel =
      object : HttpDataModel {
        val data = mutableListOf<HttpData>()
        override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
          return data.toList()
        }
      }

    // The latch is set to a count of 2 because the listener is triggered when it's added
    // and again when the range is modified. The data list is unchanged when selected range
    // is cleared.
    val onDataChangedListener = CountDownLatch(2)
    val allListener = CountDownLatch(4)
    val selectionRange = Range(0.0, 0.0)
    val dataRange = Range(0.0, 0.0)
    val fetcher = SelectionRangeDataFetcher(dataModel, selectionRange, dataRange)

    fetcher.addOnChangedListener {
      if (onDataChangedListener.count == 0L) {
        fail()
      }
      onDataChangedListener.countDown()
    }
    fetcher.addListener(
      object : SelectionRangeDataListener {
        override fun onUpdate(data: List<HttpData>) {
          if (allListener.count == 0L) {
            fail()
          }
          allListener.countDown()
        }
      }
    )

    dataModel.data.add(createFakeHttpData(1))
    selectionRange.set(0.0, 1.0)
    selectionRange.set(5.0, 6.0)
    selectionRange.clear()
    onDataChangedListener.await()
    allListener.await()
  }
}
