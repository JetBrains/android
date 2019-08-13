/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.databinding.index.BindingXmlIndex.Companion.NAME
import com.android.tools.idea.res.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.util.androidFacet
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

/**
 * Class for logging data binding related metrics.
 */
@VisibleForTesting // This class uses inheritance to override threading behavior for tests only
open class DataBindingTracker constructor(private val project: Project) : DataBindingTracker {

  override fun trackPolledMetaData() {
    if (isDataBindingEnabled()) {
      runInBackground(TrackPollingMetadataTask(project))
    }
  }

  override fun trackDataBindingCompletion(eventType: DataBindingEvent.EventType, context: DataBindingEvent.DataBindingContext) {
    if (isDataBindingEnabled()) {
      trackUserEvent(eventType, context)
    }
  }

  // TODO(b/123721754): Track whether data binding is enabled on a per module basis.
  // Currently, one module is data binding enabled = entire project is data binding enabled.
  private fun isDataBindingEnabled() = ModuleManager.getInstance(project).modules
    .mapNotNull { it.androidFacet }
    .any { DataBindingUtil.isDataBindingEnabled(it) }

  private fun trackUserEvent(eventType: DataBindingEvent.EventType, context: DataBindingEvent.DataBindingContext) {
    val studioEventBuilder = createStudioEventBuilder().apply {
      dataBindingEvent = DataBindingEvent.newBuilder().apply {
        type = eventType
        this.context = context
      }.build()
    }
    UsageTracker.log(studioEventBuilder.withProjectId(project))
  }

  private fun trackPollingEvent(eventType: DataBindingEvent.EventType,
                                metadata: DataBindingEvent.DataBindingPollMetadata?) {
    val studioEventBuilder = createStudioEventBuilder().apply {
      dataBindingEvent = DataBindingEvent.newBuilder().apply {
        type = eventType
        pollMetadata = metadata
      }.build()
    }
    UsageTracker.log(studioEventBuilder.withProjectId(project))
  }

  /**
   * This task must be run inside of a read action. Collects basic data binding usage metrics.
   */
  private inner class TrackPollingMetadataTask(val project: Project) : Runnable {
    override fun run() {
      DumbService.getInstance(project).runReadActionInSmartMode {
        var layoutCount = 0
        var importCount = 0
        var variableCount = 0
        val index = FileBasedIndex.getInstance()

        index.processAllKeys(
          NAME,
          { key ->
            index.processValues(
              NAME,
              key,
              null,
              { _, layoutInfo ->
                // TODO(b/137047493): track VIEW_BINDING_LAYOUT type layouts
                if (layoutInfo.layoutType == DATA_BINDING_LAYOUT) {
                  layoutCount++
                  importCount += layoutInfo.importCount
                  variableCount += layoutInfo.variableCount
                }
                true
              },
              GlobalSearchScope.projectScope(project)
            )
          },
          project
        )
        trackPollingEvent(DataBindingEvent.EventType.DATA_BINDING_BUILD_EVENT,
                          DataBindingEvent.DataBindingPollMetadata.newBuilder().apply {
                            dataBindingEnabled = isDataBindingEnabled()
                            layoutXmlCount = layoutCount
                            this.importCount = importCount
                            this.variableCount = variableCount
                            // We only care about Android modules (modules with an android facet).
                            moduleCount = ModuleManager.getInstance(project).modules.count { it.androidFacet != null }
                            dataBindingEnabledModuleCount = ModuleManager.getInstance(project).modules
                              .mapNotNull { it.androidFacet }
                              .count { DataBindingUtil.isDataBindingEnabled(it) }
                          }.build())
      }
    }
  }

  /**
   * Execute the target runnable on a background thread. Tests will override this to run
   * immediately.
   */
  protected open fun runInBackground(runnable: Runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable)
  }

  private fun createStudioEventBuilder() = AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.DATA_BINDING)
}
