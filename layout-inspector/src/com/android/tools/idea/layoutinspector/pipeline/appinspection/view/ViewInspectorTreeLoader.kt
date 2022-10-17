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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionTreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.GetComposablesResult
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher

/**
 * View-inspector specific logic supporting [AppInspectionTreeLoader].
 */
class ViewInspectorTreeLoader(
  private val project: Project,
  private val skiaParser: SkiaParser,
  private val viewEvent: LayoutInspectorViewProtocol.LayoutEvent,
  private val resourceLookup: ResourceLookup,
  private val process: ProcessDescriptor,
  composeResult: GetComposablesResult?,
  private val logEvent: (DynamicLayoutInspectorEventType) -> Unit,
) {
  private var folderConfig = LayoutInspectorViewProtocol.Configuration.getDefaultInstance().convert(1)

  // if true, exit immediately and return null
  private var isInterrupted = false

  private val viewNodeCreator = ViewNodeCreator(viewEvent, composeResult)

  val dynamicCapabilities: Set<InspectorClient.Capability>
    get() = viewNodeCreator.dynamicCapabilities

  @Suppress("unused") // Need to keep a reference to receive notifications
  private val lowMemoryWatcher = LowMemoryWatcher.register(
    {
      isInterrupted = true
    }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)

  fun loadComponentTree(): AndroidWindow? {
    val configuration = viewEvent.configuration
    val appContext = viewEvent.appContext
    if (configuration !== LayoutInspectorViewProtocol.Configuration.getDefaultInstance() ||
        appContext !== LayoutInspectorViewProtocol.AppContext.getDefaultInstance()) {
      folderConfig = configuration.convert(process.device.apiLevel)
      resourceLookup.updateConfiguration(folderConfig, configuration.fontScale, appContext.convert(), viewNodeCreator.strings, process)
    }
    val rootView = viewNodeCreator.createRootViewNode { isInterrupted } ?: return null
    return ViewAndroidWindow(project, skiaParser, rootView, viewEvent, folderConfig, { isInterrupted }, logEvent)
  }
}
