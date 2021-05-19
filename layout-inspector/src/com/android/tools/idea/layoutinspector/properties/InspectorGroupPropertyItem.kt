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
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.property.ptable2.PTableGroupItem

/**
 * PropertyItem instance that holds child items.
 */
class InspectorGroupPropertyItem(
  namespace: String,
  name: String,
  type: LayoutInspectorProto.Property.Type,
  value: String?,
  val classLocation: SourceLocation?,
  group: PropertySection,
  source: ResourceReference?,
  viewId: Long,
  lookup: ViewNodeAndResourceLookup,
  override val children: List<InspectorPropertyItem>
) : InspectorPropertyItem(namespace, name, name, type, value, group, source, viewId, lookup), PTableGroupItem {

  /**
   * PropertyItem instance that holds a value with resolution stack.
   *
   * The value of this item is the actual value of the property.
   * The overridden values are kept as a list of [ResolutionStackItem]s
   * which will appear as children in a properties table.
   *
   * @param stack is a map of ordered values for the property that are overridden by other values e.g. from styles.
   */
  constructor(namespace: String,
              name: String,
              type: LayoutInspectorProto.Property.Type,
              value: String?,
              classLocation: SourceLocation?,
              group: PropertySection,
              source: ResourceReference?,
              viewId: Long,
              lookup: ViewNodeAndResourceLookup,
              stack: Map<ResourceReference, String?>) :
    this(namespace, name, type, value, classLocation, group, source, viewId, lookup, mutableListOf<InspectorPropertyItem>()) {
    stack.mapTo(children as MutableList) { (reference, value) -> ResolutionStackItem(this, reference, value) }
  }
}
