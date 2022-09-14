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
package com.android.tools.idea.appinspection.inspectors.workmanager.analytics

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.google.wireless.android.sdk.stats.AppInspectionEvent.WorkManagerInspectorEvent

/**
 * An interface for tracking user actions that occur when interacting with the work manager inspector.
 */
interface WorkManagerInspectorTracker {
  fun trackTableModeSelected()
  fun trackGraphModeSelected(context: WorkManagerInspectorEvent.Context, chainInfo: WorkManagerInspectorEvent.ChainInfo)

  fun trackWorkSelected(context: WorkManagerInspectorEvent.Context)
  fun trackJumpedToSource()
  fun trackWorkCancelled()
}

class StubWorkManagerInspectorTracker : WorkManagerInspectorTracker {
  override fun trackTableModeSelected() {}
  override fun trackGraphModeSelected(context: WorkManagerInspectorEvent.Context, chainInfo: WorkManagerInspectorEvent.ChainInfo) {}
  override fun trackWorkSelected(context: WorkManagerInspectorEvent.Context) {}
  override fun trackJumpedToSource() {}
  override fun trackWorkCancelled() {}
}

fun List<WorkManagerInspectorProtocol.WorkInfo>.toChainInfo(): WorkManagerInspectorEvent.ChainInfo {
  val depthMap = mutableMapOf<String, Int>()
  for (work in this) {
    depthMap[work.id] = (work.prerequisitesList.mapNotNull { depthMap[it] }.maxOrNull() ?: 0) + 1
  }
  val worksCountByDepth = this.groupBy { depthMap[it.id] }.map { it.value.size }
  return WorkManagerInspectorEvent.ChainInfo.newBuilder()
    .setDependencyCount(sumOf { it.dependentsCount })
    .setMaxDepth(depthMap.values.maxOrNull() ?: 0)
    .setMaxWidth(worksCountByDepth.maxOrNull() ?: 0)
    .setWorkerCount(size)
    .build()
}
