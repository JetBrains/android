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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient

/**
 * A mechanism for using an [InspectorClient] and some data (dependant on the type of client) to create a [ViewNode] tree.
 */
interface TreeLoader {
  /**
   * Load the component tree corresponding to the given [data] (implementation specific). Returns the root node and an
   * implementation-specific id, which will be one of the values in the list returned by [getAllWindowIds]
   */
  fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup, client: InspectorClient): Pair<ViewNode, *>?

  fun getAllWindowIds(data: Any?, client: InspectorClient): List<*>?
}