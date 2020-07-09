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

import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.res.parseColor
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.panel.api.ActionIconButton
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.PropertyItem
import com.android.utils.HashCodes
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import java.text.DecimalFormat
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
  val initialValue: String?,

  /** Which group this attribute belongs to */
  val group: PropertySection,

  /** A reference to the resource where the value was set e.g. "@layout/my_form.xml" */
  val source: ResourceReference?,

  /** The view this attribute belongs to */
  var view: ViewNode,

  /** The inspector model this item is a part of */
  val resourceLookup: ResourceLookup

) : PropertyItem {

  constructor(namespace: String, attrName: String, type: Type, value: String?, group: PropertySection,
              source: ResourceReference?, view: ViewNode, resourceLookup: ResourceLookup) :
    this(namespace, attrName, attrName, type, value, group, source, view, resourceLookup)

  /**
   * The integer value of a dimension or -1 for other types.
   *
   * Note: for a DIMENSION_FLOAT this value should be converted using Float.fromBits(dimensionValue).
   */
  val dimensionValue: Int = when (type) {
    Type.DIMENSION -> initialValue?.toIntOrNull() ?: -1
    Type.DIMENSION_EM,
    Type.DIMENSION_DP,
    Type.DIMENSION_FLOAT,
    Type.DIMENSION_SP -> (initialValue?.toFloatOrNull() ?: Float.NaN).toRawBits()
    else -> -1
  }

  override var value: String?
    get() = when (type) {
      Type.DIMENSION -> formatDimension(dimensionValue)
      Type.DIMENSION_DP -> formatDimensionFloatDp(Float.fromBits(dimensionValue))
      Type.DIMENSION_EM -> formatDimensionFloatAsEm(Float.fromBits(dimensionValue))
      Type.DIMENSION_FLOAT -> formatDimensionFloat(Float.fromBits(dimensionValue))
      Type.DIMENSION_SP -> formatDimensionFloatAsSp(Float.fromBits(dimensionValue))
      else -> initialValue
    }
    set(_) {}

  override fun hashCode(): Int = HashCodes.mix(namespace.hashCode(), attrName.hashCode(), source?.hashCode() ?: 0)

  override fun equals(other: Any?): Boolean =
    other is InspectorPropertyItem &&
    namespace == other.namespace &&
    attrName == other.attrName &&
    source == other.source &&
    javaClass == other.javaClass

  override val helpSupport = object : HelpSupport {
    override fun browse() {
      val location = resourceLookup.findFileLocations(this@InspectorPropertyItem, 1).singleOrNull() ?: return
      location.navigatable?.navigate(true)
    }
  }

  override val colorButton = createColorButton()

  private fun formatDimension(pixels: Int): String? {
    if (pixels == -1 || pixels == Int.MIN_VALUE || pixels == Int.MAX_VALUE) {
      // -1 means not supported for some attributes e.g. baseline of View
      // MIN_VALUE means not supported for some attributes e.g. layout_marginStart in ViewGroup.MarginLayoutParams
      // MAX-VALUE means not specified for some attributes e.g. maxWidth of TextView
      return initialValue
    }
    if (resourceLookup.dpi <= 0) {
      // If we are unable to get the dpi from the device, just show pixels
      return "${pixels}px"
    }
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.PIXELS -> "${pixels}px"
      DimensionUnits.DP -> "${pixels * 160 / resourceLookup.dpi}dp"
    }
  }

  private fun formatDimensionFloat(pixels: Float): String? {
    if (pixels.isNaN()) {
      return initialValue
    }
    if (resourceLookup.dpi <= 0) {
      // If we are unable to get the dpi from the device, just show pixels
      return "${formatFloat(pixels)}px"
    }
    if (name == ATTR_TEXT_SIZE && resourceLookup.fontScale != 0.0f && PropertiesSettings.dimensionUnits == DimensionUnits.DP) {
      return "${DecimalFormat("0.0").format(pixels * pixelsToSpFactor)}sp"
    }
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.PIXELS -> "${formatFloat(pixels)}px"
      DimensionUnits.DP -> "${formatFloat(pixels * 160.0f / resourceLookup.dpi)}dp"
    }
  }

  private fun formatDimensionFloatDp(dp: Float): String? {
    if (dp.isNaN()) {
      return initialValue
    }
    if (resourceLookup.dpi <= 0) {
      // If we are unable to get the dpi from the device, just show dp
      return "${formatFloat(dp)}dp"
    }
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.DP -> "${formatFloat(dp)}dp"
      DimensionUnits.PIXELS -> "${formatFloat(dp / 160.0f * resourceLookup.dpi)}px"
    }
  }

  private fun formatDimensionFloatAsSp(sp: Float): String? {
    if (sp.isNaN()) {
      return initialValue
    }
    if (resourceLookup.dpi <= 0) {
      // If we are unable to get the dpi from the device, just show in sp
      return "${formatFloat(sp)}sp"
    }
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.PIXELS -> "${formatFloat(sp / pixelsToSpFactor)}px"
      DimensionUnits.DP -> "${formatFloat(sp)}sp"
    }
  }

  private fun formatDimensionFloatAsEm(em: Float): String? {
    if (em.isNaN()) {
      return initialValue
    }
    return "${formatFloat(em)}em"
  }

  private val pixelsToSpFactor: Float
    get() = 160.0f / resourceLookup.fontScale / resourceLookup.dpi

  private fun formatFloat(value: Float): String = if (value == 0.0f) "0" else DecimalFormat("0.0##").format(value)

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
      get() {
        property.resourceLookup.resolveAsIcon(property)?.let { return it }
        val value = property.value
        val color = value?.let { parseColor(value) } ?: return null
        // TODO: Convert this into JBUI.scale(ColorIcon(RESOURCE_ICON_SIZE, color, false)) when JBCachingScalableIcon extends JBScalableIcon
        return ColorIcon(RESOURCE_ICON_SIZE, color, false).scale(JBUIScale.scale(1f))
      }
  }
}
