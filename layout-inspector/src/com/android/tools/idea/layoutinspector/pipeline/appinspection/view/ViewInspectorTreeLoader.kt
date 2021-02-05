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

import com.android.tools.idea.layoutinspector.SkiaParserService
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionTreeLoader
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val LOAD_TIMEOUT = TimeUnit.SECONDS.toMillis(20)

/**
 * View-inspector specific logic supporting [AppInspectionTreeLoader].
 */
class ViewInspectorTreeLoader(
  private val project: Project,
  private val skiaParser: SkiaParserService,
  private val viewEvent: LayoutInspectorViewProtocol.LayoutEvent,
  private val resourceLookup: ResourceLookup,
  composeEvent: LayoutInspectorComposeProtocol.GetComposablesResponse?,
  private val updateScreenshotType: (LayoutInspectorViewProtocol.Screenshot.Type) -> Unit,
) {
  private val loadStartTime = AtomicLong(-1)

  // if true, exit immediately and return null
  private var isInterrupted = false

  private val viewNodeCreator = ViewNodeCreator(viewEvent, composeEvent)

  @Suppress("unused") // Need to keep a reference to receive notifications
  private val lowMemoryWatcher = LowMemoryWatcher.register(
    {
      isInterrupted = true
    }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)

  fun loadComponentTree(): AndroidWindow? {
    val time = System.currentTimeMillis()
    if (time - loadStartTime.get() < LOAD_TIMEOUT) {
      return null
    }
    try {
      resourceLookup.updateConfiguration(viewEvent.appContext.convert(), viewNodeCreator.strings)
      val rootView = viewNodeCreator.createRootViewNode { isInterrupted } ?: return null
      return ViewAndroidWindow(project, skiaParser, rootView, viewEvent, { isInterrupted }, updateScreenshotType)
    }
    finally {
      loadStartTime.set(0)
    }
  }
}
