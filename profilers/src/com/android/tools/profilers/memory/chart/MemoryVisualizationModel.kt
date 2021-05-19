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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.model.formatter.BaseAxisFormatter
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter

class MemoryVisualizationModel {
  var axisFilter: XAxisFilter

  init {
    axisFilter = XAxisFilter.ALLOC_SIZE
  }

  fun isSizeAxis(): Boolean {
    return axisFilter == XAxisFilter.ALLOC_SIZE || axisFilter == XAxisFilter.TOTAL_SIZE
  }

  fun formatter(): BaseAxisFormatter {
    return if (isSizeAxis()) {
      MemoryAxisFormatter.DEFAULT
    }
    else {
      SingleUnitAxisFormatter(1, 10, 1, "")
    }
  }

  enum class XAxisFilter(private val filterName: String) {
    TOTAL_COUNT("Total Remaining Count"), TOTAL_SIZE("Total Remaining Size"), ALLOC_SIZE("Allocation Size"), ALLOC_COUNT(
      "Allocation Count");

    override fun toString(): String {
      return filterName
    }
  }
}