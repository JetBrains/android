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
package com.android.tools.idea.streaming.core

import com.android.utils.HashCodes
import kotlin.math.roundToInt

/**
 * Persistent state representing a [AbstractDisplayPanel] or a [SplitPanel].
 * The no-argument constructor is used by the XML deserializer.
 */
internal class PanelState private constructor() {

  // The displayId and splitPanel properties are mutually exclusive. Exactly one of them is not null.
  var displayId: Int? = null
  var splitPanel: SplitPanelState? = null

  constructor(displayId: Int) : this() {
    this.displayId = displayId
  }

  constructor(splitType: SplitType, proportion: Double, firstComponent: PanelState, secondComponent: PanelState) : this() {
    this.splitPanel = SplitPanelState(splitType, proportion, firstComponent, secondComponent)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PanelState
    return displayId == other.displayId && splitPanel == other.splitPanel
  }

  override fun hashCode(): Int {
    return HashCodes.mix(displayId ?: 0, splitPanel?.hashCode() ?: 0)
  }

  /**
   * Persistent state representing a [SplitPanel]. The no-argument constructor is used by the XML deserializer.
   */
  class SplitPanelState private constructor() {

    var splitType: SplitType = SplitType.HORIZONTAL
    var proportion: Double = 0.5
    lateinit var firstComponent: PanelState
    lateinit var secondComponent: PanelState

    constructor(splitType: SplitType, proportion: Double, firstComponent: PanelState, secondComponent: PanelState)
        : this() {
      this.splitType = splitType
      this.proportion = proportion
      this.firstComponent = firstComponent
      this.secondComponent = secondComponent
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as SplitPanelState
      return splitType == other.splitType && proportion == other.proportion &&
             firstComponent == other.firstComponent && secondComponent == other.secondComponent
    }

    override fun hashCode(): Int {
      return HashCodes.mix(splitType.hashCode(), (proportion * Int.MAX_VALUE).roundToInt())
    }
  }
}