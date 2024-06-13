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
package com.android.tools.idea.layoutinspector.properties

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.property.ptable.PTableGroupItem

/** PropertyItem instance that holds child items. */
open class InspectorGroupPropertyItem(
  namespace: String,
  name: String,
  type: PropertyType,
  value: String?,
  open val classLocation: SourceLocation?,
  section: PropertySection,
  source: ResourceReference?,
  viewId: Long,
  lookup: ViewNodeAndResourceLookup,
  override val children: List<InspectorPropertyItem>,
) :
  InspectorPropertyItem(namespace, name, name, type, value, section, source, viewId, lookup),
  PTableGroupItem {

  /** This item need a ResolutionEditor for display */
  override val needsResolutionEditor: Boolean
    get() = true
}
