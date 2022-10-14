/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.property.panel.api.FlagPropertyItem
import com.android.tools.property.panel.api.FlagsPropertyGroupItem
import com.android.tools.property.panel.api.FlagsPropertyItem
import com.android.tools.property.panel.api.PropertyItem
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.stream
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.attrs.AttributeDefinition

/**
 * Special version of [NlPropertyItem] for flag attributes.
 *
 * This class will generate a [NlFlagPropertyItem] for each flag for a particular flags attribute.
 * There is code to read and write a single flag as a boolean value.
 */
open class NlFlagsPropertyItem(
  namespace: String,
  name: String,
  type: NlPropertyType,
  private val attrDefinition: AttributeDefinition,
  componentName: String,
  libraryName: String,
  model: NlPropertiesModel,
  components: List<NlComponent>,
  optionalValue1: Any? = null,
  optionalValue2: Any? = null
) : NlPropertyItem(namespace, name, type, attrDefinition, componentName, libraryName, model, components, optionalValue1, optionalValue2),
    FlagsPropertyItem<NlFlagPropertyItem> {
  private val _flags = mutableListOf<NlFlagPropertyItem>()
  private val _lastValues = mutableSetOf<String>()
  private var _lastValue: String? = null
  private var _lastMaskValue: Int = 0
  private var _lastFormattedValue: String? = null

  private val lastValues: Set<String>
    get() {
      cacheValues()
      return _lastValues
    }

  override val maskValue: Int
    get() {
      cacheValues()
      return _lastMaskValue
    }

  val formattedValue: String
    get() {
      cacheValues()
      return _lastFormattedValue!!
    }

  override val children: List<NlFlagPropertyItem>
    get() {
      if (_flags.isEmpty()) {
          attrDefinition.values.mapTo(_flags) { NlFlagPropertyItem(this, it, lookupMaskValue(it)) }
      }
      return _flags
    }

  override var value: String?
    get() = super.value
    set(value) {
      super.value = value
      if (_lastValue != value) {
        invalidateCache()
      }
    }

  override fun flag(itemName: String): NlFlagPropertyItem {
    return children.find { it.name == itemName } ?: throw IllegalArgumentException(itemName)
  }

  override fun validate(text: String?): Pair<EditingErrorCategory, String> {
    var flagsValue = (text ?: rawValue).nullize() ?: return EDITOR_NO_ERROR
    if (flagsValue.startsWith("@")) {
      val result = validateResourceReference(flagsValue)
      if (result != null) {
        return result
      }
      flagsValue = resolveValue(flagsValue) ?: flagsValue
    }
    val unknown = VALUE_SPLITTER.split(flagsValue).toSet().minus(children.map { it.name }).map { "'$it'" }
    return when {
      unknown.isEmpty() -> EDITOR_NO_ERROR
      unknown.size == 1 -> Pair(EditingErrorCategory.ERROR, "Invalid value: ${unknown.first()}")
      else -> Pair(EditingErrorCategory.ERROR, "Invalid values: ${Joiner.on(", ").join(unknown)}")
    }
  }

  fun isFlagSet(flag: NlFlagPropertyItem) = lastValues.contains(flag.name)

  fun setFlag(flag: NlFlagPropertyItem, on: Boolean) {
    val add = if (on) flag.name else null
    val remove = if (on) null else flag.name
    val values = lastValues
    val builder = StringBuilder()
    // Enumerate over the values in attrDefinition to get a consistent order:
    attrDefinition.values.stream()
      .filter { it == add || (values.contains(it) && it != remove) }
      .forEach { builder.append("|").append(it) }
    value = if (builder.isEmpty()) null else builder.substring(1)
  }

  private fun cacheValues() {
    val rawValue = value
    if (_lastValues.isEmpty() && _lastValue == rawValue) {
      return
    }
    _lastValues.clear()
    val resolved = resolvedValue
    var formattedValue = "[]"
    var maskValue = 0
    if (!resolved.isNullOrEmpty()) {
      val valueList = VALUE_SPLITTER.splitToList(StringUtil.notNullize(resolved))
      _lastValues.addAll(valueList)
      formattedValue = "[" + Joiner.on(", ").join(valueList) + "]"
      valueList.forEach { maskValue = maskValue or lookupMaskValue(it) }
    }

    _lastValue = rawValue
    _lastMaskValue = maskValue
    _lastFormattedValue = formattedValue
  }

  private fun invalidateCache() {
    _lastValues.clear()
    _lastValue = null
    _lastFormattedValue = null
    _lastMaskValue = 0
  }

  private fun lookupMaskValue(value: String): Int {
    val mappedValue = getValueMapping(value)
    if (mappedValue != null) {
      return mappedValue
    }
    val index = ArrayUtil.indexOf(attrDefinition.values, value)
    return if (index < 0) 0 else 1 shl index
  }

  private fun getValueMapping(value: String): Int? {
    var mappedValue = attrDefinition.getValueMapping(value) ?: return null

    // b/68335041 The values for gravity center_vertical and center_horizontal are
    // special values that just means that the "axis is specified".
    // See documentation for Gravity.java in the framework.
    //
    // This implies that the mapped values for top & bottom also include the mapped
    // value for vertical_center in attrs.xml. The UI would then show center_vertical
    // checked when top or bottom was selected. This does not make sense in the UI.
    // Similarly with left, right, start, end and center_horizontal.
    //
    // Override the value mapping for gravity: top, bottom, left, right, start, end
    // to NOT include the center values.
    if (namespace == SdkConstants.ANDROID_URI && name == SdkConstants.ATTR_GRAVITY && GRAVITY_OVERRIDES.contains(value)) {
      mappedValue = mappedValue and GRAVITY_MAPPED_VALUE_CENTER.inv()
    }
    return mappedValue
  }

  companion object {
    private val GRAVITY_OVERRIDES = Sets.newHashSet("top", "bottom", "right", "left", "start", "end")
    private const val GRAVITY_MAPPED_VALUE_CENTER = 0x11
    private val VALUE_SPLITTER = Splitter.on("|").trimResults()
  }
}

/**
 * Flags property that can be expanded in a properties table.
 */
class NlFlagsPropertyGroupItem(
  namespace: String,
  name: String,
  type: NlPropertyType,
  attrDefinition: AttributeDefinition,
  componentName: String,
  libraryName: String,
  model: NlPropertiesModel,
  components: List<NlComponent>,
  optionalValue1: Any? = null,
  optionalValue2: Any? = null
) : NlFlagsPropertyItem(namespace, name, type, attrDefinition, componentName, libraryName, model, components, optionalValue1,
                        optionalValue2), FlagsPropertyGroupItem<NlFlagPropertyItem>

/**
 * Specifies a single flag in a flags attribute.
 *
 * A generated [PropertyItem] which can be used in an editor in the property inspector.
 */
class NlFlagPropertyItem(override val flags: NlFlagsPropertyItem, name: String, override val maskValue: Int) :
  NlPropertyItem(flags.namespace, name, NlPropertyType.BOOLEAN, null, flags.componentName, "", flags.model,
                 flags.components, flags.optionalValue1, flags.optionalValue2),
  FlagPropertyItem {

  override val isReference: Boolean
    get() = false

  override val rawValue: String?
    get() = if (actualValue) SdkConstants.VALUE_TRUE else SdkConstants.VALUE_FALSE

  override var value: String?
    get() = rawValue
    set(value) { actualValue = value?.equals(SdkConstants.VALUE_TRUE, true) == true }

  override var actualValue: Boolean
    get() = flags.isFlagSet(this)
    set(value) = flags.setFlag(this, value)

  override val effectiveValue: Boolean
    get() = if (maskValue == 0) flags.maskValue == 0 else maskValue and flags.maskValue == maskValue
}
