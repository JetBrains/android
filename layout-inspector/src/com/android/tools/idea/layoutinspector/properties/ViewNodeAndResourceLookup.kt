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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterReference
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.property.ptable.PTableGroupModification
import kotlinx.coroutines.CoroutineScope

/** Provides [ViewNode] and [ResourceLookup] for an [InspectorPropertyItem]. */
interface ViewNodeAndResourceLookup {
  /** Find the [ViewNode] associated with the specified id. */
  operator fun get(id: Long): ViewNode?

  /** Provide a resource lookup. */
  val resourceLookup: ResourceLookup

  /** The current selected node */
  val selection: ViewNode?

  /** An coroutine scope for use by property items */
  val scope: CoroutineScope

  /**
   * Perform a lookup of a compose parameter item given a parameter reference
   *
   * @param callback containing a [ParameterGroupItem] with the wanted child elements and the
   *   [PTableGroupModification] performed on that [ParameterGroupItem] to retrieve those child
   *   elements.
   *
   * TODO: b/182196505 we should split out view PropertyItems and Compose ParameterItems.
   */
  fun resolve(
    rootId: Long,
    reference: ParameterReference,
    startIndex: Int,
    maxElements: Int,
    callback: (ParameterGroupItem?, PTableGroupModification?) -> Unit,
  ) {}
}
