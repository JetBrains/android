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
import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.property.panel.api.ActionIconButton
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.PropertyItem
import com.android.utils.HashCodes
import java.text.DecimalFormat

private const val DEFAULT_DENSITY = 160 // Same as Density.MEDIUM.dpiValue
private const val DEFAULT_DENSITY_FLOAT = 160.0f

/** A [PropertyItem] in the inspector with a snapshot of the value. */
open class InspectorPropertyItem(

  /** The namespace of the attribute e.g. "http://schemas.android.com/apk/res/android" */
  override val namespace: String,

  /** The name of the attribute */
  val attrName: String,

  /** The name displayed in a property table */
  override val name: String,

  /** The type of the attribute */
  initialType: PropertyType,

  /** The value of the attribute when the snapshot was taken */
  var snapshotValue: String?,

  /** Which section this attribute belongs to in the attributes tool window */
  val section: PropertySection,

  /** A reference to the resource where the value was set e.g. "@layout/my_form.xml" */
  val source: ResourceReference?,

  /** The view this property belongs to */
  val viewId: Long,

  /** The property [ViewNode] and [ResourceLookup] */
  val lookup: ViewNodeAndResourceLookup,
) : PropertyItem {

  constructor(
    namespace: String,
    attrName: String,
    type: PropertyType,
    value: String?,
    group: PropertySection,
    source: ResourceReference?,
    viewId: Long,
    lookup: ViewNodeAndResourceLookup,
  ) : this(namespace, attrName, attrName, type, value, group, source, viewId, lookup)

  /** The type of the attribute */
  var type = initialType
    private set(value) {
      field = value
      dimensionValue = computeDimensionValue(value)
    }

  /** The source locations for this [source] reference (populated late) */
  val sourceLocations = mutableListOf<SourceLocation>()

  /** Return true if this property has details that will require a ResolutionEditor */
  open val needsResolutionEditor: Boolean
    get() = false

  /**
   * The integer value of a dimension or -1 for other types.
   *
   * Note: for a DIMENSION_FLOAT this value should be converted using
   * Float.fromBits(dimensionValue).
   */
  var dimensionValue: Int = computeDimensionValue(initialType)
    private set

  private fun computeDimensionValue(type: PropertyType): Int =
    when (type) {
      PropertyType.DIMENSION -> snapshotValue?.toIntOrNull() ?: -1
      PropertyType.DIMENSION_EM,
      PropertyType.DIMENSION_DP,
      PropertyType.DIMENSION_FLOAT,
      PropertyType.DIMENSION_SP -> (snapshotValue?.toFloatOrNull() ?: Float.NaN).toRawBits()
      else -> -1
    }

  override var value: String?
    get() =
      when (type) {
        PropertyType.DIMENSION -> formatDimension(dimensionValue)
        PropertyType.DIMENSION_DP -> formatDimensionFloatDp(Float.fromBits(dimensionValue))
        PropertyType.DIMENSION_EM -> formatDimensionFloatAsEm(Float.fromBits(dimensionValue))
        PropertyType.DIMENSION_FLOAT -> formatDimensionFloat(Float.fromBits(dimensionValue))
        PropertyType.DIMENSION_SP -> formatDimensionFloatAsSp(Float.fromBits(dimensionValue))
        else -> snapshotValue
      }
    set(newValue) {
      snapshotValue = newValue
      dimensionValue = computeDimensionValue(type)
    }

  override fun hashCode(): Int =
    HashCodes.mix(namespace.hashCode(), attrName.hashCode(), source?.hashCode() ?: 0)

  override fun equals(other: Any?): Boolean =
    other is InspectorPropertyItem &&
      namespace == other.namespace &&
      attrName == other.attrName &&
      source == other.source &&
      javaClass == other.javaClass

  override val helpSupport =
    object : HelpSupport {
      override fun browse() {
        val location = sourceLocations.firstOrNull()
        location?.navigatable?.navigate(true)
      }
    }

  /** The color Icon button used for some types of properties (populated late) */
  override var colorButton: ActionIconButton? = null

  private fun formatDimension(pixels: Int): String? {
    if (pixels == -1 || pixels == Int.MIN_VALUE || pixels == Int.MAX_VALUE) {
      // -1 means not supported for some attributes e.g. baseline of View
      // MIN_VALUE means not supported for some attributes e.g. layout_marginStart in
      // ViewGroup.MarginLayoutParams
      // MAX-VALUE means not specified for some attributes e.g. maxWidth of TextView
      return snapshotValue
    }
    val resourceLookup = lookup.resourceLookup
    // If we are unable to get the dpi from the device, just show pixels
    val dpi = resourceLookup.dpi ?: return "${pixels}px"
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.PIXELS -> "${pixels}px"
      DimensionUnits.DP -> "${pixels * DEFAULT_DENSITY / dpi}dp"
    }
  }

  private fun formatDimensionFloat(pixels: Float): String? {
    if (pixels.isNaN()) {
      return snapshotValue
    }
    val resourceLookup = lookup.resourceLookup
    // If we are unable to get the dpi from the device, just show pixels
    val dpi = resourceLookup.dpi ?: return "${formatFloat(pixels)}px"
    if (
      name == ATTR_TEXT_SIZE &&
        resourceLookup.fontScale != 0.0f &&
        PropertiesSettings.dimensionUnits == DimensionUnits.DP
    ) {
      val spFactor = pixelsToSpFactor
      if (spFactor != null) {
        return "${DecimalFormat("0.0").format(pixels * spFactor)}sp"
      }
    }
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.PIXELS -> "${formatFloat(pixels)}px"
      DimensionUnits.DP -> "${formatFloat(pixels * DEFAULT_DENSITY_FLOAT / dpi)}dp"
    }
  }

  private fun formatDimensionFloatDp(dp: Float): String? {
    if (dp.isNaN()) {
      return snapshotValue
    }
    val resourceLookup = lookup.resourceLookup
    // If we are unable to get the dpi from the device, just show dp
    val dpi = resourceLookup.dpi ?: return "${formatFloat(dp)}dp"
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.DP -> "${formatFloat(dp)}dp"
      DimensionUnits.PIXELS -> "${formatFloat(dp / DEFAULT_DENSITY_FLOAT * dpi)}px"
    }
  }

  private fun formatDimensionFloatAsSp(sp: Float): String? {
    if (sp.isNaN()) {
      return snapshotValue
    }
    // If we are unable to get the dpi or scale factor from the device, just show in sp
    val spFactor = pixelsToSpFactor ?: return "${formatFloat(sp)}sp"
    return when (PropertiesSettings.dimensionUnits) {
      DimensionUnits.PIXELS -> "${formatFloat(sp / spFactor)}px"
      DimensionUnits.DP -> "${formatFloat(sp)}sp"
    }
  }

  private fun formatDimensionFloatAsEm(em: Float): String? {
    if (em.isNaN()) {
      return snapshotValue
    }
    return "${formatFloat(em)}em"
  }

  private val pixelsToSpFactor: Float?
    get() {
      val dpi = lookup.resourceLookup.dpi ?: return null
      val fontScale = lookup.resourceLookup.fontScale ?: return null
      return DEFAULT_DENSITY_FLOAT / fontScale / dpi
    }

  private fun formatFloat(value: Float): String =
    if (value == 0.0f) "0" else DecimalFormat("0.0##").format(value)

  @Slow
  fun resolveDimensionType(view: ViewNode) {
    if (
      (type == PropertyType.INT32 || type == PropertyType.FLOAT) &&
        lookup.resourceLookup.isDimension(view, name)
    ) {
      type =
        if (type == PropertyType.INT32) PropertyType.DIMENSION else PropertyType.DIMENSION_FLOAT
    }
  }
}
