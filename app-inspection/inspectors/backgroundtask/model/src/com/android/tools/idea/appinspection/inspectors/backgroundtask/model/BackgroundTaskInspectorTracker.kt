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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent

/** Tracks interesting user interaction points with BTI. */
interface BackgroundTaskInspectorTracker {
  fun trackTableModeSelected()
  fun trackGraphModeSelected(
    context: BackgroundTaskInspectorEvent.Context,
    chainInfo: BackgroundTaskInspectorEvent.ChainInfo
  )
  fun trackJumpedToSource()
  fun trackWorkCancelled()

  fun trackWorkSelected(context: BackgroundTaskInspectorEvent.Context)
  fun trackJobSelected()
  fun trackJobUnderWorkSelected()
  fun trackAlarmSelected()
  fun trackWakeLockSelected()
  fun trackWakeLockUnderJobSelected()
}

fun List<WorkManagerInspectorProtocol.WorkInfo>.toChainInfo():
  BackgroundTaskInspectorEvent.ChainInfo {
  val depthMap = mutableMapOf<String, Int>()
  for (work in this) {
    depthMap[work.id] = (work.prerequisitesList.mapNotNull { depthMap[it] }.maxOrNull() ?: 0) + 1
  }
  val worksCountByDepth = this.groupBy { depthMap[it.id] }.map { it.value.size }
  return BackgroundTaskInspectorEvent.ChainInfo.newBuilder()
    .setDependencyCount(sumOf { it.dependentsCount })
    .setMaxDepth(depthMap.values.maxOrNull() ?: 0)
    .setMaxWidth(worksCountByDepth.maxOrNull() ?: 0)
    .setWorkerCount(size)
    .build()
}
