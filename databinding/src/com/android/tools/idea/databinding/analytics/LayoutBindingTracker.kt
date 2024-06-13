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
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.databinding.index.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.databinding.index.BindingLayoutType.PLAIN_LAYOUT
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.project.LayoutBindingEnabledFacetsProvider
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.util.androidFacet
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.Callable

/** Class for logging data binding and view binding related metrics. */
@VisibleForTesting // This class uses inheritance to override threading behavior for tests only
open class LayoutBindingTracker(private val project: Project) : DataBindingTracker {

  override fun trackPolledMetaData() {
    if (enabledFacetsProvider.getAllBindingEnabledFacets().isNotEmpty()) {
      runInBackground(TrackPollingMetadataTask(project))
    }
  }

  override fun trackDataBindingCompletion(
    eventType: DataBindingEvent.EventType,
    context: DataBindingEvent.DataBindingContext,
  ) {
    if (isDataBindingEnabled()) {
      trackUserEvent(eventType, context)
    }
  }

  private val enabledFacetsProvider = LayoutBindingEnabledFacetsProvider.getInstance(project)

  private fun isDataBindingEnabled() =
    enabledFacetsProvider.getDataBindingEnabledFacets().isNotEmpty()

  private fun isViewBindingEnabled() =
    enabledFacetsProvider.getViewBindingEnabledFacets().isNotEmpty()

  private fun trackUserEvent(
    eventType: DataBindingEvent.EventType,
    context: DataBindingEvent.DataBindingContext,
  ) {
    val studioEventBuilder =
      createStudioEventBuilder().apply {
        dataBindingEvent =
          DataBindingEvent.newBuilder()
            .apply {
              type = eventType
              this.context = context
            }
            .build()
      }
    UsageTracker.log(studioEventBuilder.withProjectId(project))
  }

  private fun trackPollingEvent(
    eventType: DataBindingEvent.EventType,
    dataBindingMetadata: DataBindingEvent.DataBindingPollMetadata?,
    viewBindingMetaData: DataBindingEvent.ViewBindingPollMetadata?,
  ) {
    val studioEventBuilder =
      createStudioEventBuilder().apply {
        dataBindingEvent =
          DataBindingEvent.newBuilder()
            .apply {
              type = eventType
              pollMetadata = dataBindingMetadata
              viewBindingMetadata = viewBindingMetaData
            }
            .build()
      }
    UsageTracker.log(studioEventBuilder.withProjectId(project))
  }

  /** This task must be run inside of a read action. Collects basic data binding usage metrics. */
  private inner class TrackPollingMetadataTask(val project: Project) : Runnable {
    override fun run() {
      val bindingXmlData =
        ReadAction.nonBlocking(
            Callable {
              // BindingXmlIndex uses the shared "single entry" index. As such, calling
              // FileBasedIndex.getAllKeys() for it returns a key for every file in the project, not
              // just those that are binding files.
              // Instead, the best that can be done here is to scope down to only XML files and then
              // call into BindingXmlIndex. There will obviously be false positive keys, but the
              // index will just return a null value for them.
              FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .mapNotNull {
                  ProgressManager.checkCanceled()
                  BindingXmlIndex.getDataForFile(project, it)
                }
            }
          )
          .inSmartMode(project)
          .executeSynchronously()

      val dataBindingLayouts =
        bindingXmlData.asSequence().filter { it.layoutType == DATA_BINDING_LAYOUT }

      val dataBindingLayoutCount = dataBindingLayouts.count()
      val importCount = dataBindingLayouts.sumOf { it.imports.size }
      val variableCount = dataBindingLayouts.sumOf { it.variables.size }

      val viewBindingLayoutCount =
        bindingXmlData.count { it.layoutType == PLAIN_LAYOUT && !it.viewBindingIgnore }

      trackPollingEvent(
        DataBindingEvent.EventType.DATA_BINDING_BUILD_EVENT,
        DataBindingEvent.DataBindingPollMetadata.newBuilder()
          .apply {
            dataBindingEnabled = isDataBindingEnabled()
            layoutXmlCount = dataBindingLayoutCount
            this.importCount = importCount
            this.variableCount = variableCount
            // We only care about Android modules (modules with an Android facet).
            moduleCount =
              ModuleManager.getInstance(project).modules.count { it.androidFacet != null }
            dataBindingEnabledModuleCount =
              ModuleManager.getInstance(project).modules.count {
                it.androidFacet?.let(DataBindingUtil::isDataBindingEnabled) ?: false
              }
          }
          .build(),
        DataBindingEvent.ViewBindingPollMetadata.newBuilder()
          .apply {
            viewBindingEnabled = isViewBindingEnabled()
            layoutXmlCount = viewBindingLayoutCount
          }
          .build(),
      )
    }
  }

  /**
   * Execute the target runnable on a background thread. Tests will override this to run
   * immediately.
   */
  protected open fun runInBackground(runnable: Runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable)
  }

  private fun createStudioEventBuilder() =
    AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.DATA_BINDING)
}
