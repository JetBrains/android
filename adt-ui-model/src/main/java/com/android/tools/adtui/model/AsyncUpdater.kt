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
package com.android.tools.adtui.model

object AsyncUpdater {
  /**
   * Creates an updater for results `R` that can be too expensive to compute and refresh
   * per frame. The result is computed asynchronously and refreshed in the UI when it's
   * ready, and repeated until it's up-to-date.
   *
   * @param runOnUi run the provided action on the UI thread
   * @param runOnBackground run the provided action on the background thread
   * @param initResult extract initial result
   * @param nextResult compute the next result from a previous result
   * @param updateResult given a result, update it on the UI
   */
  @JvmStatic
  fun<R> by(runOnUi: (Runnable) -> Unit,
            runOnBackground: (Runnable) -> Unit,
            initResult: () -> R,
            nextResult: (R) -> R,
            updateResult: (R) -> Unit): () -> Unit {
    var lastTimestamp = 0
    var isUpdating = false

    return {
      lastTimestamp++
      if (!isUpdating) {
        isUpdating = true
        runOnBackground {
          try {
            var result = initResult()
            while (true) {
              val targetTimestamp = lastTimestamp
              result = nextResult(result)
              runOnUi { updateResult(result) }
              if (targetTimestamp == lastTimestamp) {
                break
              }
            }
          }
          finally {
            runOnUi { isUpdating = false }
          }
        }
      }
    }
  }
}