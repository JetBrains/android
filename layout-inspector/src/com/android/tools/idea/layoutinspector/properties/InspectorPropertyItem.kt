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
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.panel.api.ActionIconButton
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.PropertyItem
import com.android.utils.HashCodes
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.Icon

/**
 * A [PropertyItem] in the inspector with a snapshot of the value.
 */
open class InspectorPropertyItem(

  /** The namespace of the attribute e.g. "http://schemas.android.com/apk/res/android" */
  override val namespace: String,

  /** The name of the attribute */
  val attrName: String,

  /** The name displayed in a property table */
  override val name: String,

  /** The type of the attribute */
  val type: Type,

  /** The value of the attribute when the snapshot was taken */
  override var value: String?,

  /** If the attribute value was specified in a layout file i.e. by the user */
  val isDeclared: Boolean,

  /** A reference to the resource where the value was set e.g. "@layout/my_form.xml" */
  val source: ResourceReference?,

  /** The view this attribute belongs to */
  val view: ViewNode,

  /** The properties model this item is a part of */
  val model: InspectorPropertiesModel

) : PropertyItem {

  override fun hashCode(): Int = HashCodes.mix(namespace.hashCode(), attrName.hashCode(), source?.hashCode() ?: 0)

  override fun equals(other: Any?): Boolean =
    other is InspectorPropertyItem &&
    namespace == other.namespace &&
    attrName == other.attrName &&
    source == other.source &&
    javaClass == other.javaClass

  override val helpSupport = object : HelpSupport {
    override fun browse() {
      val location = resourceLookup?.findFileLocations(this@InspectorPropertyItem, 1)?.singleOrNull() ?: return
      location.navigatable?.navigate(true)
    }
  }

  val resourceLookup: ResourceLookup?
    get() = model.layoutInspector?.layoutInspectorModel?.resourceLookup

  override val colorButton = createColorButton()

  private fun createColorButton(): ActionIconButton? =
    when (type) {
      Type.COLOR,
      Type.DRAWABLE -> value?.let { ColorActionIconButton(this) }
      else -> null
    }

  private class ColorActionIconButton(private val property: InspectorPropertyItem): ActionIconButton {
    override val actionButtonFocusable = false
    override val action: AnAction? = null
    override val actionIcon: Icon?
      get() = property.resourceLookup?.resolveAsIcon(property)
  }
}
