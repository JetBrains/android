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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.ComponentTreeData
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewInspectorTreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import org.jetbrains.annotations.VisibleForTesting

/**
 * A [TreeLoader] that uses data from the [AppInspectionInspectorClient] to fetch a view tree from
 * an API 29+ device, and parses it into [ViewNode]s.
 */
class AppInspectionTreeLoader(
  private val notificationModel: NotificationModel,
  private val logEvent: (DynamicLayoutInspectorEventType) -> Unit,
  @VisibleForTesting var skiaParser: SkiaParser,
) : TreeLoader {
  override fun loadComponentTree(
    data: Any?,
    resourceLookup: ResourceLookup,
    process: ProcessDescriptor,
  ): ComponentTreeData? {
    if (data is ViewLayoutInspectorClient.Data) {
      val treeLoader =
        ViewInspectorTreeLoader(
          notificationModel,
          skiaParser,
          data.viewEvent,
          resourceLookup,
          process,
          data.composeEvent,
          logEvent,
        )
      val window = treeLoader.loadComponentTree()
      return ComponentTreeData(window, data.generation, treeLoader.dynamicCapabilities)
    }
    return null
  }

  override fun getAllWindowIds(data: Any?): List<Long>? {
    if (data is ViewLayoutInspectorClient.Data) {
      return data.rootIds
    }
    return null
  }
}
