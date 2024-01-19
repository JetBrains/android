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

/**
 * Specifies an overridden property value.
 *
 * These value are usually found in styles where the value has been overridden by another style or a
 * direct attribute assignment of the xml tag.
 */
class ResolutionStackItem(
  property: InspectorGroupPropertyItem,
  reference: ResourceReference,
  value: String?,
) :
  InspectorPropertyItem(
    property.namespace,
    property.name,
    "", // The name of the PTableItem is empty, such that is isn't repeated in the properties table.
    property.type,
    value,
    property.section,
    reference,
    property.viewId,
    property.lookup,
  ) {

  /** This item need a ResolutionEditor for display */
  override val needsResolutionEditor: Boolean
    get() = true
}
