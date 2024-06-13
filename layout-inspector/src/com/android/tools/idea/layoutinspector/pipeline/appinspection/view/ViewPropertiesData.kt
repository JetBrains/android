/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.colorToString
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.FlagValue
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Property
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertyGroup
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import java.awt.Color

private const val SOME_UNKNOWN_ANIM_VALUE = "@anim/?"
private const val SOME_UNKNOWN_ANIMATOR_VALUE = "@animator/?"
private const val SOME_UNKNOWN_DRAWABLE_VALUE = "@drawable/?"
private const val SOME_UNKNOWN_INTERPOLATOR_VALUE = "@interpolator/?"
private const val ANIMATION_PACKAGE = "android.view.animation"
private const val FRAMEWORK_INTERPOLATOR_PREFIX = "@android:interpolator"

class ViewPropertiesData(
  /** The properties associated with a view. */
  val properties: PropertiesTable<InspectorPropertyItem>,

  /** The resolution stacks for each (namespace, property-name) pair. */
  val resolutionStacks: Table<String, String, List<ResourceReference>>,

  /** The className of known object types: (ANIM, ANIMATOR, INTERPOLATOR, DRAWABLE) */
  val classNames: Table<String, String, String>,
)

/** Bridge between incoming proto data and classes expected by the Studio properties framework. */
class ViewPropertiesDataGenerator(
  private val stringTable: StringTableImpl,
  private val properties: PropertyGroup,
  private val lookup: ViewNodeAndResourceLookup,
) {
  // TODO: The module namespace probably should be retrieved from the module. Use the layout
  // namespace for now:
  private val layout = stringTable[properties.layout]
  private val propertyTable = HashBasedTable.create<String, String, InspectorPropertyItem>()
  private val classNamesTable = HashBasedTable.create<String, String, String>()
  private val resolutionStackTable =
    HashBasedTable.create<String, String, List<ResourceReference>>()
  private val resolutionStackMap = mutableMapOf<List<Resource>, List<ResourceReference>>()
  private val viewId = properties.viewId
  private val packageNameToNamespaceUri = mutableMapOf<String, String>()

  fun generate(): ViewPropertiesData {
    for (property in properties.propertyList) {
      val item = convert(property)
      addItemToTable(item)
      addResolutionStack(item, property)
    }
    return ViewPropertiesData(
      PropertiesTable.create(propertyTable),
      resolutionStackTable,
      classNamesTable,
    )
  }

  private fun addItemToTable(item: InspectorPropertyItem) {
    // Hack: revisit this when/if namespaces are enabled.
    //
    // For now:
    //  - assume that a component with a shadow attribute will set the corresponding framework
    // attribute at runtime.
    //  - remove the framework attribute that is being shadowed.
    // We know this is the case for: AppCompatButton.backgroundTint
    when (item.namespace) {
      ANDROID_URI -> if (propertyTable.containsColumn(item.name)) return
      else -> propertyTable.remove(ANDROID_URI, item.name)
    }
    propertyTable.put(item.namespace, item.name, item)
  }

  private fun convert(property: Property): InspectorPropertyItem {
    val namespace = toNamespaceUri(stringTable[property.namespace])
    val name = stringTable[property.name]
    val isDeclared =
      property.source == properties.layout && property.source != Resource.getDefaultInstance()
    val source = stringTable[property.source]
    val group =
      when {
        property.isLayout -> PropertySection.LAYOUT
        isDeclared -> PropertySection.DECLARED
        else -> PropertySection.DEFAULT
      }
    val value: String? =
      when (property.type) {
        Property.Type.STRING,
        Property.Type.INT_ENUM -> stringTable[property.int32Value]
        Property.Type.GRAVITY,
        Property.Type.INT_FLAG -> fromFlags(property.flagValue)
        Property.Type.BOOLEAN -> fromBoolean(property)?.toString()
        Property.Type.CHAR -> fromChar(property)?.toString()
        Property.Type.BYTE,
        Property.Type.INT16,
        Property.Type.INT32 -> fromInt32(property)?.toString()
        Property.Type.INT64 -> fromInt64(property)?.toString()
        Property.Type.DOUBLE -> fromDouble(property)?.toString()
        Property.Type.FLOAT -> fromFloat(property)?.toString()
        Property.Type.RESOURCE -> fromResource(property, layout)
        Property.Type.COLOR -> fromColor(property)

        // For the following types we are unable to get the value from the agent. For now store the
        // className:
        Property.Type.DRAWABLE,
        Property.Type.ANIM,
        Property.Type.ANIMATOR,
        Property.Type.INTERPOLATOR -> fromKnownObjectType(property)
        else -> ""
      }
    val type = property.type.convert()
    return InspectorPropertyItem(namespace, name, type, value, group, source, viewId, lookup)
  }

  private fun toNamespaceUri(packageName: String): String =
    packageNameToNamespaceUri.computeIfAbsent(packageName) {
      if (packageName.isNotEmpty()) ResourceNamespace.fromPackageName(packageName).xmlNamespaceUri
      else ""
    }

  private fun fromResource(property: Property, layout: ResourceReference?): String {
    val reference = stringTable[property.resourceValue]
    val url =
      layout?.let { reference?.getRelativeResourceUrl(layout.namespace) } ?: reference?.resourceUrl
    return url?.toString() ?: ""
  }

  private fun fromFlags(flagValue: FlagValue): String {
    return flagValue.flagList.joinToString("|") { stringTable[it] }
  }

  private fun fromBoolean(property: Property): Boolean? {
    val intValue = fromInt32(property) ?: return null
    return intValue != 0
  }

  private fun fromChar(property: Property): Char? {
    val intValue = fromInt32(property) ?: return null
    return intValue.toChar()
  }

  private fun fromInt32(property: Property): Int? {
    return if (property.valueCase == Property.ValueCase.INT32_VALUE) property.int32Value else null
  }

  private fun fromInt64(property: Property): Long? {
    return if (property.valueCase == Property.ValueCase.INT64_VALUE) property.int64Value else null
  }

  private fun fromDouble(property: Property): Double? {
    return if (property.valueCase == Property.ValueCase.DOUBLE_VALUE) property.doubleValue else null
  }

  private fun fromFloat(property: Property): Float? {
    return if (property.valueCase == Property.ValueCase.FLOAT_VALUE) property.floatValue else null
  }

  private fun fromColor(property: Property): String? {
    val intValue = fromInt32(property) ?: return null
    return colorToString(Color(intValue, true))
  }

  private fun fromKnownObjectType(property: Property): String? {
    val name = stringTable[property.name]
    val className = stringTable[property.int32Value]
    if (className.isNotEmpty()) {
      // TODO: Handle attribute namespaces i.e. the hardcoded ANDROID_URI below
      classNamesTable.put(SdkConstants.ANDROID_URI, name, className)
    }
    return when (property.type) {
      Property.Type.ANIM -> SOME_UNKNOWN_ANIM_VALUE
      Property.Type.ANIMATOR -> SOME_UNKNOWN_ANIMATOR_VALUE
      Property.Type.DRAWABLE -> SOME_UNKNOWN_DRAWABLE_VALUE
      Property.Type.INTERPOLATOR ->
        valueFromInterpolatorClass(className) ?: SOME_UNKNOWN_INTERPOLATOR_VALUE
      else -> null
    }
  }

  /**
   * Map an interpolator className into the resource id for the interpolator.
   *
   * Some interpolator implementations do not have any variants. We can map such an interpolator to
   * a resource value even if we don't have access to the source.
   *
   * Note: the following interpolator classes have variants:
   * - AccelerateInterpolator
   * - DecelerateInterpolator
   * - PathInterpolator and are represented by more than 1 resource id depending on the variant.
   */
  private fun valueFromInterpolatorClass(className: String?): String? =
    when (className) {
      "${ANIMATION_PACKAGE}.AccelerateDecelerateInterpolator" ->
        "${FRAMEWORK_INTERPOLATOR_PREFIX}/accelerate_decelerate"
      "${ANIMATION_PACKAGE}.AnticipateInterpolator" -> "${FRAMEWORK_INTERPOLATOR_PREFIX}/anticipate"
      "${ANIMATION_PACKAGE}.AnticipateOvershootInterpolator" ->
        "${FRAMEWORK_INTERPOLATOR_PREFIX}/anticipate_overshoot"
      "${ANIMATION_PACKAGE}.BounceInterpolator" -> "${FRAMEWORK_INTERPOLATOR_PREFIX}/bounce"
      "${ANIMATION_PACKAGE}.CycleInterpolator" -> "${FRAMEWORK_INTERPOLATOR_PREFIX}/cycle"
      "${ANIMATION_PACKAGE}.LinearInterpolator" -> "${FRAMEWORK_INTERPOLATOR_PREFIX}/linear"
      "${ANIMATION_PACKAGE}.OvershootInterpolator" -> "${FRAMEWORK_INTERPOLATOR_PREFIX}/overshoot"
      else -> null
    }

  /** Add the resolutionStack for this property to [resolutionStackTable] */
  private fun addResolutionStack(item: InspectorPropertyItem, property: Property) {
    var encodedResolutionStack = property.resolutionStackList
    if (encodedResolutionStack.isEmpty()) {
      if (property.source == Resource.getDefaultInstance()) {
        return
      }
      encodedResolutionStack = listOf(property.source)
    }
    // Many resolution stacks from the agent are identical. Conserve memory by only converting
    // unique stacks:
    val resolutionStack =
      resolutionStackMap.computeIfAbsent(encodedResolutionStack) {
        it.mapNotNull { res -> stringTable[res] }
      }
    if (resolutionStack.isEmpty()) {
      return
    }

    resolutionStackTable.put(item.namespace, item.name, resolutionStack)
  }
}
