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
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.NAMESPACE_INTERNAL
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.properties.addInternalProperties
import com.android.tools.idea.layoutinspector.resource.SourceLocation
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
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Table
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import java.awt.Color
import java.util.concurrent.Future

private val INT32_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.INT32_VALUE_FIELD_NUMBER)
private val INT64_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.INT64_VALUE_FIELD_NUMBER)
private val DOUBLE_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.DOUBLE_VALUE_FIELD_NUMBER)
private val FLOAT_FIELD_DESCRIPTOR = Property.getDescriptor().findFieldByNumber(Property.FLOAT_VALUE_FIELD_NUMBER)
private val EMPTY_PROPERTIES_DATA = DefaultPropertiesProvider.PropertiesData(
  PropertiesTable.emptyTable(), ImmutableTable.of(), ImmutableTable.of())

private const val SOME_UNKNOWN_ANIM_VALUE = "@anim/?"
private const val SOME_UNKNOWN_ANIMATOR_VALUE = "@animator/?"
private const val SOME_UNKNOWN_DRAWABLE_VALUE = "@drawable/?"
private const val SOME_UNKNOWN_INTERPOLATOR_VALUE = "@interpolator/?"
private const val ANIMATION_PACKAGE = "android.view.animation"
private const val FRAMEWORK_INTERPOLATOR_PREFIX = "@android:interpolator"

class DefaultPropertiesProvider(
  private val client: InspectorClient,
  private val model: InspectorModel
): PropertiesProvider {

  private var lastRequestedViewId = -1L
  private var lastGeneration = 0
  private val cache = mutableMapOf<Long, PropertiesData>()

  init {
    client.register(Common.Event.EventGroupIds.PROPERTIES, ::loadProperties)
    model.connectionListeners.add {
      cache.clear()
      lastGeneration = 0
    }
  }

  override val resultListeners = mutableListOf<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode): Future<*> {
    if (!client.isConnected) {
      lastRequestedViewId = -1
      firePropertiesProvided(view, EMPTY_PROPERTIES_DATA)
      return Futures.immediateFuture(null)
    }
    lastRequestedViewId = view.drawId
    if (lastGeneration == model.lastGeneration) {
      val properties = cache[view.drawId]
      if (properties != null) {
        firePropertiesProvided(view, properties)
        return Futures.immediateFuture(null)
      }
    }
    if (!client.isCapturing) {
      // When the last properties are out of date in snapshot mode, a new snapshot is expected in the near future:
      // Do nothing.
      return Futures.immediateFuture(null)
    }
    else {
      // When the last properties are out of date in live mode:
      // Send a request for the wanted properties.
      val inspectorCommand = LayoutInspectorCommand.newBuilder()
        .setType(LayoutInspectorCommand.Type.GET_PROPERTIES)
        .setViewId(view.drawId)
        .build()
      return ApplicationManager.getApplication().executeOnPooledThread { client.execute(inspectorCommand) }
    }
  }

  private fun loadProperties(event: Any) {
    val transportEvent = event as? LayoutInspectorEvent ?: return
    if (!transportEvent.hasProperties()) {
      return
    }
    val viewId = transportEvent.properties.viewId
    val generation = transportEvent.properties.generation
    if (generation < lastGeneration || generation < model.lastGeneration) {
      return
    }
    val generator = Generator(transportEvent.properties, model)
    val propertiesData = generator.generate()
    if (generation > lastGeneration) {
      cache.clear()
      lastGeneration = generation
    }
    cache[viewId] = propertiesData
    if (lastRequestedViewId == viewId && generation == model.lastGeneration) {
      val view = model[viewId] ?: return
      firePropertiesProvided(view, propertiesData)
    }
  }

  private fun firePropertiesProvided(view: ViewNode, propertiesData: PropertiesData) {
    val properties = completeProperties(view, propertiesData)
    resultListeners.forEach { it(this, view, properties) }
  }

  /**
   * Complete the properties table with information from the [ViewNode].
   *
   * The properties were loaded from the agent, but the following cannot be completed before the [ViewNode] is known:
   * - The agent does not specify which attributes is a dimension type. Get that from the Studio side.
   * - Add the standard internal attributes from the [ViewNode].
   * - Add a call location to all known object types where the className is known.
   * - Create resolution stack items based on the resolution stack received from the agent.
   */
  private fun completeProperties(view: ViewNode, propertiesData: PropertiesData): PropertiesTable<InspectorPropertyItem> {
    val properties = propertiesData.properties
    if (properties.isEmpty || properties.getByNamespace(NAMESPACE_INTERNAL).isNotEmpty()) {
      return properties
    }
    if (view !is ComposeViewNode) {
      properties.values.forEach { it.resolveDimensionType(view) }
    }
    addInternalProperties(properties, view, model)
    ReadAction.run<Exception> {
      propertiesData.classNames.cellSet().mapNotNull { cell ->
        properties.getOrNull(cell.rowKey!!, cell.columnKey!!)?.let { convertToItemWithClassLocation(it, cell.value!!) }
      }.forEach { properties.put(it) }
      propertiesData.resolutionStacks.cellSet().mapNotNull { cell ->
        properties.getOrNull(cell.rowKey!!, cell.columnKey!!)?.let { convertToResolutionStackItem(it, view, cell.value!!) }
      }.forEach { properties.put(it) }
    }
    return properties
  }

  /**
   * Generate items with a classLocation for known object types.
   *
   * This strictly could have happened up front because the [ViewNode] is not needed for computing the
   * [SourceLocation] for the class used for this value. However the computation takes time so this will
   * delay that cost until it is needed to show the properties for the containing [ViewNode].
   */
  private fun convertToItemWithClassLocation(
    item: InspectorPropertyItem,
    className: String
  ): InspectorPropertyItem? {
    val classLocation = model.resourceLookup.resolveClassNameAsSourceLocation(className) ?: return null
    return InspectorGroupPropertyItem(item.namespace, item.name, item.type, item.initialValue, classLocation,
                                      item.group, item.source, item.viewId, item.lookup, emptyList())
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
  private fun convertToResolutionStackItem(
    item: InspectorPropertyItem,
    view: ViewNode,
    resolutionStack: List<ResourceReference>
  ): InspectorPropertyItem? {
    val map = resolutionStack
      .associateWith { model.resourceLookup.findAttributeValue(item, view, it) }
      .filterValues { it != null }
      .toMutableMap()
    val firstRef = map.keys.firstOrNull()
    if (firstRef != null && firstRef == item.source) {
      map.remove(firstRef)
    }
    val classLocation: SourceLocation? = (item as? InspectorGroupPropertyItem)?.classLocation
    if (map.isNotEmpty() || item.source != null || classLocation != null) {
      // Make this item a group item such that the details are hidden until the item is expanded.
      // Note that there doesn't have to be sub items in the group. A source location or class location is enough to trigger this.
      return InspectorGroupPropertyItem(item.namespace, item.name, item.type, item.initialValue, classLocation,
                                        item.group, item.source, item.viewId, item.lookup, map)
    }
    return null
  }

  /**
   * Generator of properties received from the agent.
   */
  private class Generator(
    private val properties: PropertyEvent,
    private val lookup: ViewNodeAndResourceLookup
  ) {
    // TODO: The module namespace probably should be retrieved from the module. Use the layout namespace for now:
    private val stringTable = StringTableImpl(properties.stringList)
    private val layout = stringTable[properties.layout]
    private val propertyTable = HashBasedTable.create<String, String, InspectorPropertyItem>()
    private val classNamesTable = HashBasedTable.create<String, String, String>()
    private val resolutionStackTable = HashBasedTable.create<String, String, List<ResourceReference>>()
    private val resolutionStackMap = mutableMapOf<List<Resource>, List<ResourceReference>>()
    private val viewId = properties.viewId

    /**
     * Read the data from the properties event and return a [PropertiesData] instance.
     */
    fun generate(): PropertiesData {
      for (property in properties.propertyList) {
        add(generateItem(property))
        addResolutionStack(property)
      }
      return PropertiesData(PropertiesTable.create(propertyTable), resolutionStackTable, classNamesTable)
    }

    private fun generateItem(property: Property): InspectorPropertyItem {
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
        Type.CHAR -> fromChar(property)?.toString()
        Type.BYTE,
        Type.INT16,
        Type.INT32 -> fromInt32(property)?.toString()
        Type.INT64 -> fromInt64(property)?.toString()
        Type.DIMENSION_EM,
        Type.DIMENSION_DP,
        Type.DIMENSION_FLOAT,
        Type.DIMENSION_SP -> fromFloat(property)?.toString()
        Type.DOUBLE -> fromDouble(property)?.toString()
        Type.FLOAT -> fromFloat(property)?.toString()
        Type.RESOURCE -> fromResource(property, layout)
        Type.COLOR -> fromColor(property)

        // For the following types we are unable to get the value from the agent. For now store the className:
        Type.DRAWABLE,
        Type.ANIM,
        Type.ANIMATOR,
        Type.INTERPOLATOR -> fromKnownObjectType(property)
        else -> ""
      }
      val type = property.type
      if (property.elementList.isEmpty()) {
        // TODO: Handle attribute namespaces i.e. the hardcoded ANDROID_URI below
        return InspectorPropertyItem(ANDROID_URI, name, type, value, group, source, viewId, lookup)
      }
      val children = property.elementList.map { generateItem(it) }
      return InspectorGroupPropertyItem(ANDROID_URI, name, type, value, null, group, source, viewId, lookup, children)
    }

    /**
     * Add the resolutionStack for this property to [resolutionStackTable]
     */
    private fun addResolutionStack(property: Property) {
      var encodedResolutionStack = property.resolutionStackList
      if (encodedResolutionStack.isEmpty()) {
        if (property.source == Resource.getDefaultInstance()) {
          return
        }
        encodedResolutionStack = listOf(property.source)
      }
      // Many resolution stacks from the agent are identical. Conserve memory by only converting unique stacks:
      val resolutionStack = resolutionStackMap.computeIfAbsent(encodedResolutionStack) { it.mapNotNull { res -> stringTable[res] } }
      if (resolutionStack.isEmpty()) {
        return
      }

      val name = stringTable[property.name]

      // TODO: Handle attribute namespaces i.e. the hardcoded ANDROID_URI below
      resolutionStackTable.put(ANDROID_URI, name, resolutionStack)
    }

    private fun fromKnownObjectType(property: Property): String? {
      val name = stringTable[property.name]
      val className = stringTable[property.int32Value]
      if (className.isNotEmpty()) {
        // TODO: Handle attribute namespaces i.e. the hardcoded ANDROID_URI below
        classNamesTable.put(ANDROID_URI, name, className)
      }
      return when (property.type) {
        Type.ANIM -> SOME_UNKNOWN_ANIM_VALUE
        Type.ANIMATOR -> SOME_UNKNOWN_ANIMATOR_VALUE
        Type.DRAWABLE -> SOME_UNKNOWN_DRAWABLE_VALUE
        Type.INTERPOLATOR -> valueFromInterpolatorClass(className) ?: SOME_UNKNOWN_INTERPOLATOR_VALUE
        else -> null
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

    private fun fromChar(property: Property): Char? {
      val intValue = fromInt32(property) ?: return null
      return intValue.toChar()
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
      propertyTable.put(item.namespace, item.name, item)
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
    private fun valueFromInterpolatorClass(className: String?): String? =
      when (className) {
        "$ANIMATION_PACKAGE.AccelerateDecelerateInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/accelerate_decelerate"
        "$ANIMATION_PACKAGE.AnticipateInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/anticipate"
        "$ANIMATION_PACKAGE.AnticipateOvershootInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/anticipate_overshoot"
        "$ANIMATION_PACKAGE.BounceInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/bounce"
        "$ANIMATION_PACKAGE.CycleInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/cycle"
        "$ANIMATION_PACKAGE.LinearInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/linear"
        "$ANIMATION_PACKAGE.OvershootInterpolator" -> "$FRAMEWORK_INTERPOLATOR_PREFIX/overshoot"
        else -> null
      }
  }

  /**
   * The result from [Generator.generate()].
   */
  internal class PropertiesData(
    /**
     * The properties of a ViewNode.
     */
    val properties: PropertiesTable<InspectorPropertyItem>,

    /**
     * The resolution stacks for each (namespace, property-name) pair.
     */
    val resolutionStacks: Table<String, String, List<ResourceReference>>,

    /**
     * The className of known object types: (ANIM, ANIMATOR, INTERPOLATOR, DRAWABLE)
     */
    val classNames: Table<String, String, String>
  )
}
