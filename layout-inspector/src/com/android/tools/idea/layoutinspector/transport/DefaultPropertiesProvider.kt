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
package com.android.tools.idea.layoutinspector.transport

import com.android.SdkConstants.ANDROID_URI
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.common.StringTableImpl
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.res.colorToString
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.FlagValue
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.PropertyEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Resource
import com.android.tools.profiler.proto.Common
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.intellij.openapi.application.ApplicationManager
import java.awt.Color

private val INT32_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.INT32_VALUE_FIELD_NUMBER)
private val INT64_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.INT64_VALUE_FIELD_NUMBER)
private val DOUBLE_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.DOUBLE_VALUE_FIELD_NUMBER)
private val FLOAT_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.FLOAT_VALUE_FIELD_NUMBER)

private const val SOME_UNKNOWN_ANIM_VALUE = "@anim/?"
private const val SOME_UNKNOWN_ANIMATOR_VALUE = "@animator/?"
private const val SOME_UNKNOWN_DRAWABLE_VALUE = "@drawable/?"
private const val SOME_UNKNOWN_INTERPOLATOR_VALUE = "@interpolator/?"
private const val ANIMATION_PACKAGE = "android.view.animation"
private const val FRAMEWORK_INTERPOLATOR_PREFIX = "@android:interpolator"

class DefaultPropertiesProvider(
  private val client: InspectorClient,
  private val resourceLookup: ResourceLookup
): PropertiesProvider {

  private var lastRequestedView: ViewNode? = null

  init {
    client.register(Common.Event.EventGroupIds.PROPERTIES, ::loadProperties)
  }

  override val resultListeners = mutableListOf<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode) {
    if (!client.isConnected) {
      lastRequestedView = null
      firePropertiesProvided(view, PropertiesTable.emptyTable())
      return
    }
    val inspectorCommand = LayoutInspectorCommand.newBuilder()
      .setType(LayoutInspectorCommand.Type.GET_PROPERTIES)
      .setViewId(view.drawId)
      .build()
    client.execute(inspectorCommand)
    lastRequestedView = view
  }

  private fun loadProperties(event: Any) {
    val transportEvent = event as? LayoutInspectorEvent ?: return
    val view = lastRequestedView
    if (view == null || transportEvent.properties.viewId != view.drawId) {
      return
    }
    val generator = Generator(transportEvent.properties, view, resourceLookup)
    val properties = PropertiesTable.create(generator.generate())
    firePropertiesProvided(view, properties)
  }

  private fun firePropertiesProvided(view: ViewNode, properties: PropertiesTable<InspectorPropertyItem>) {
    resultListeners.forEach { it(this, view, properties) }
  }

  private class Generator(
    private val properties: PropertyEvent,
    private val view: ViewNode,
    private val resourceLookup: ResourceLookup
  ) {
    // TODO: The module namespace probably should be retrieved from the module. Use the layout namespace for now:
    private val stringTable = StringTableImpl(properties.stringList)
    private val layout = stringTable[properties.layout]
    private val table = HashBasedTable.create<String, String, InspectorPropertyItem>()

    fun generate(): Table<String, String, InspectorPropertyItem> {
      for (property in properties.propertyList) {
        val name = stringTable[property.name]
        val isDeclared = property.source == properties.layout &&
                         property.source != Resource.getDefaultInstance()
        val source = stringTable[property.source]
        val group = when {
          property.isLayout -> PropertySection.LAYOUT
          isDeclared -> PropertySection.DECLARED
          else -> PropertySection.DEFAULT
        }
        val value: String? = when (property.type) {
          Type.STRING,
          Type.INT_ENUM -> stringTable[property.int32Value]
          Type.GRAVITY,
          Type.INT_FLAG -> fromFlags(property.flagValue)
          Type.BOOLEAN -> fromBoolean(property)?.toString()
          Type.BYTE,
          Type.CHAR,
          Type.INT16,
          Type.INT32 -> fromInt32(property)?.toString()
          Type.INT64 -> fromInt64(property)?.toString()
          Type.DOUBLE -> fromDouble(property)?.toString()
          Type.FLOAT -> fromFloat(property)?.toString()
          Type.RESOURCE -> fromResource(property, layout)
          Type.COLOR -> fromColor(property)

          // We are unable to get the value from the agent. Use a placeholder.
          Type.DRAWABLE -> SOME_UNKNOWN_DRAWABLE_VALUE
          Type.ANIM -> SOME_UNKNOWN_ANIM_VALUE
          Type.ANIMATOR -> SOME_UNKNOWN_ANIMATOR_VALUE
          Type.INTERPOLATOR -> SOME_UNKNOWN_INTERPOLATOR_VALUE
          else -> ""
        }
        // TODO: Handle attribute namespaces i.e. the hardcoded ANDROID_URI below
        add(
          InspectorPropertyItem(ANDROID_URI, name, name, property.type, value, group, source, view, resourceLookup))
      }
      ApplicationManager.getApplication().runReadAction { generateItemsForResolutionStack() }
      return table
    }

    /**
     * Generate items for displaying the resolution stack.
     *
     * Each property may include a resolution stack i.e. places and values in e.g. styles
     * that are overridden by other attribute or style assignments.
     *
     * In the inspector properties table we have chosen to show these as independent values
     * in collapsible sections for each property. The resolution stack is received as a list
     * of resource references that may (or may not) set the value of the current attribute.
     * The code below will lookup the value (from PSI source files) of each possible resource
     * reference. If any values were found the original property item is replaced with a group
     * item with children consisting of the available resource references where a value was
     * found.
     */
    private fun generateItemsForResolutionStack() {
      for (property in properties.propertyList) {
        val name = stringTable[property.name]
        val item = table[ANDROID_URI, name]
        val map = property.resolutionStackList
          .mapNotNull { stringTable[it] }
          .associateWith { resourceLookup.findAttributeValue(item, it) }
          .filterValues { it != null }
          .toMutableMap()
        val firstRef = map.keys.firstOrNull()
        if (firstRef != null && firstRef == item.source) {
          map.remove(firstRef)
        }
        val className: String? = when (property.type) {
          Type.ANIM,
          Type.ANIMATOR,
          Type.INTERPOLATOR,
          Type.DRAWABLE -> stringTable[property.int32Value]
          else -> null  // TODO offer information from other object types
        }
        val value: String? = when (property.type) {
          // Attempt to find the drawable value from source code, since it is impossible to get from the agent.
          Type.ANIM,
          Type.ANIMATOR,
          Type.DRAWABLE -> item.source?.let { resourceLookup.findAttributeValue(item, it) } ?: item.value
          Type.INTERPOLATOR  -> item.source?.let { resourceLookup.findAttributeValue(item, it) } ?: valueFromInterpolatorClass(className)
          else -> item.value
        }
        val classLocation = className?.let { resourceLookup.resolveClassNameAsSourceLocation(it) }
        if (map.isNotEmpty() || item.source != null || className != null || value != item.value) {
          add(InspectorGroupPropertyItem(ANDROID_URI,
                                         name,
                                         property.type,
                                         value,
                                         classLocation,
                                         item.group,
                                         item.source,
                                         view,
                                         resourceLookup,
                                         map))
        }
      }
    }

    private fun fromResource(property: Property, layout: ResourceReference?): String {
      val reference = stringTable[property.resourceValue]
      val url = layout?.let { reference?.getRelativeResourceUrl(layout.namespace) } ?: reference?.resourceUrl
      return url?.toString() ?: ""
    }

    private fun fromFlags(flagValue: FlagValue): String {
      return flagValue.flagList.joinToString("|") { stringTable[it] }
    }

    private fun fromBoolean(property: Property): Boolean? {
      val intValue = fromInt32(property) ?: return null
      return intValue != 0
    }

    private fun fromInt32(property: Property): Int? {
      val intValue = property.int32Value
      if (intValue == 0 && !property.hasField(INT32_FIELD_DESCRIPTOR)) {
        return null
      }
      return intValue
    }

    private fun fromInt64(property: Property): Long? {
      val intValue = property.int64Value
      if (intValue == 0L && !property.hasField(INT64_FIELD_DESCRIPTOR)) {
        return null
      }
      return intValue
    }

    private fun fromDouble(property: Property): Double? {
      val doubleValue = property.doubleValue
      if (doubleValue == 0.0 && !property.hasField(DOUBLE_FIELD_DESCRIPTOR)) {
        return null
      }
      return doubleValue
    }

    private fun fromFloat(property: Property): Float? {
      val floatValue = property.floatValue
      if (floatValue == 0.0f && !property.hasField(FLOAT_FIELD_DESCRIPTOR)) {
        return null
      }
      return floatValue
    }

    private fun fromColor(property: Property): String? {
      val intValue = fromInt32(property) ?: return null
      return colorToString(Color(intValue))
    }

    private fun add(item: InspectorPropertyItem) {
      table.put(item.namespace, item.name, item)
    }

    /**
     * Map an interpolator className into the resource id for the interpolator.
     *
     * Some interpolator implementations do not have any variants. We can map such an
     * interpolator to a resource value even if we don't have access to the source.
     *
     * Note: the following interpolator classes have variants:
     *  - AccelerateInterpolator
     *  - DecelerateInterpolator
     *  - PathInterpolator
     * and are represented by more than 1 resource id depending on the variant.
     */
    private fun valueFromInterpolatorClass(className: String?): String =
      when (className) {
        "$ANIMATION_PACKAGE.AccelerateDecelerateInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/accelerate_decelerate"
        "$ANIMATION_PACKAGE.AnticipateInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/anticipate"
        "$ANIMATION_PACKAGE.AnticipateOvershootInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/anticipate_overshoot"
        "$ANIMATION_PACKAGE.BounceInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/bounce"
        "$ANIMATION_PACKAGE.CycleInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/cycle"
        "$ANIMATION_PACKAGE.LinearInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/linear"
        "$ANIMATION_PACKAGE.OvershootInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/overshoot"
        else -> SOME_UNKNOWN_INTERPOLATOR_VALUE
      }
  }
}
