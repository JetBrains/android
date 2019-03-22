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

import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.property.panel.api.PropertyItem
import com.android.utils.HashCodes

/**
 * A [PropertyItem] in the inspector with a snapshot of the value.
 */
data class InspectorPropertyItem(

  /** The namespace of the attribute e.g. "http://schemas.android.com/apk/res/android" */
  override val namespace: String,

  /** The name of the attribute */
  override val name: String,

  /** The value of the attribute when the snapshot was taken */
  override var value: String?,

  /** If the attribute value was specified in a layout file i.e. by the user */
  val isDeclared: Boolean,

  /** A reference to the resource where the value was set e.g. "@layout/my_form.xml" */
  val source: String?,

  /** The view this attribute belongs to */
  val view: InspectorView,

  /** The properties model this item is a part of */
  val model: InspectorPropertiesModel

) : PropertyItem {

  override fun hashCode(): Int = HashCodes.mix(namespace.hashCode(), name.hashCode())

  override fun equals(other: Any?): Boolean =
    other is InspectorPropertyItem && namespace == other.namespace && name == other.name
}
