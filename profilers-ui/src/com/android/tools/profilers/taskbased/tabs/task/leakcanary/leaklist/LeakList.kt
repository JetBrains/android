/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider

/** Renders the main view of the LeakCanary leak list, including the filter bar, dividing line, and the filtered leak table. */
@Composable
fun LeakListView(leakCanaryModel: LeakCanaryModel) {
  Column {
    val leaks by leakCanaryModel.leaks.collectAsState()
    val filteredLeaks by leakCanaryModel.filteredLeaks.collectAsState()
    val isRecording by leakCanaryModel.isRecording.collectAsState()
    val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
    val hasActiveFilter = leaks.size != filteredLeaks.size

    LeakCanaryFilterBar(leakCanaryModel)
    ToolWindowHorizontalDivider()
    LeakListContent(
      leaks = filteredLeaks,
      selectedLeak = selectedLeak,
      isRecording = isRecording,
      hasActiveFilter = hasActiveFilter,
      onLeakSelection = leakCanaryModel::onLeakSelection,
    )
  }
}
