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

import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.SkiaParserService
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewInspectorTreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.intellij.openapi.project.Project

/**
 * A [TreeLoader] that uses data from the [AppInspectionInspectorClient] to fetch a view tree from an API 29+ device, and parses it into
 * [ViewNode]s.
 */
class AppInspectionTreeLoader(
  private val project: Project,
  private val skiaParser: SkiaParserService = SkiaParser) : TreeLoader {
  override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup): Pair<AndroidWindow?, Int>? {
    if (data is ViewLayoutInspectorClient.Data) {
      val window = ViewInspectorTreeLoader(
        project,
        skiaParser,
        data.viewEvent,
        resourceLookup,
        data.composeEvent,
        data.updateScreenshotType
      ).loadComponentTree()
      return window to data.generation
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
