/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Box selection model for dispatching box selection listeners.
 */
class BoxSelectionModel(selectionRange: Range, viewRange: Range) : RangeSelectionModel(selectionRange, viewRange) {
  private val boxSelectionListeners = mutableListOf<BoxSelectionListener>()

  fun addBoxSelectionListener(boxSelectionListener: BoxSelectionListener) {
    boxSelectionListeners.add(boxSelectionListener)
  }

  fun selectionCreated(durationUs: Long, trackCount: Int) {
    boxSelectionListeners.forEach { it.boxSelectionCreated(durationUs, trackCount) }
  }
}