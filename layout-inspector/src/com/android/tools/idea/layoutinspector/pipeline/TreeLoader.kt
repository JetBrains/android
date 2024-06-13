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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.resource.ResourceLookup

/**
 * A mechanism for using an [InspectorClient] and some data (dependant on the type of client) to
 * create a [ViewNode] tree.
 */
interface TreeLoader {
  /** Load the component tree corresponding to the given [data] (implementation specific). */
  fun loadComponentTree(
    data: Any?,
    resourceLookup: ResourceLookup,
    process: ProcessDescriptor,
  ): ComponentTreeData?

  fun getAllWindowIds(data: Any?): List<*>?
}

/** The result of [TreeLoader.loadComponentTree]. */
data class ComponentTreeData(
  /** The loaded [AndroidWindow], or null if all windows are gone. */
  val window: AndroidWindow?,

  /**
   * A generation id, that can be used to ensure other responses (e.g. properties) are up to date
   */
  val generation: Int,

  /** Dynamic capabilities based on the loaded data. */
  val dynamicCapabilities: Set<Capability>,
)
