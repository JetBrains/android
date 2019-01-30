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

import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.utils.HashCodes

data class InspectorPropertyItem(
  override val namespace: String,
  override val name: String,
  override var value: String?,
  val view: InspectorView,
  val model: InspectorPropertiesModel
) : PropertyItem {

  override fun hashCode(): Int = HashCodes.mix(namespace.hashCode(), name.hashCode())

  override fun equals(other: Any?): Boolean =
    other is InspectorPropertyItem && namespace == other.namespace && name == other.name
}
