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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.PropertyItem
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.stream
import org.jetbrains.android.dom.attrs.AttributeDefinition

/**
 * Special version of [NelePropertyItem] for flag attributes.
 *
 * This class will generate a [NeleFlagPropertyItem] for each flag for a particular flags attribute.
 * There is code to read and write a single flag as a boolean value.
 */
class NeleFlagsPropertyItem(
  namespace: String,
  name: String,
  type: NelePropertyType,
  private val attrDefinition: AttributeDefinition,
  libraryName: String,
  model: NelePropertiesModel,
  components: List<NlComponent>
) : NelePropertyItem(namespace, name, type, attrDefinition, libraryName, model, components) {
  private val _flags = mutableListOf<NeleFlagPropertyItem>()
  private val _lastValues = mutableSetOf<String>()
  private var _lastValue: String? = null
  private var _lastMaskValue: Int = 0
  private var _lastFormattedValue: String? = null

  private val lastValues: Set<String>
    get() {
      cacheValues()
      return _lastValues
    }

  val maskValue: Int
    get() {
      cacheValues()
      return _lastMaskValue
    }

  val formattedValue: String
    get() {
      cacheValues()
      return _lastFormattedValue!!
    }

  val flags: List<NeleFlagPropertyItem>
    get() {
      if (_flags.isEmpty()) {
          attrDefinition.values.mapTo(_flags, { NeleFlagPropertyItem(this, it, lookupMaskValue(it)) })
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

  fun flag(itemName: String): NeleFlagPropertyItem {
    return flags.find { it.name == itemName } ?: throw IllegalArgumentException(itemName)
  }

  fun isFlagSet(flag: NeleFlagPropertyItem) = lastValues.contains(flag.name)

  fun setFlag(flag: NeleFlagPropertyItem, on: Boolean) {
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
 * Specifies a single flag in a flags attribute.
 *
 * A generated [PropertyItem] which can be used in an editor in the property inspector.
 */
class NeleFlagPropertyItem(val flags: NeleFlagsPropertyItem, override val name: String, val maskValue: Int): PropertyItem {
  override val namespace: String
    get() = flags.namespace

  val type: NelePropertyType
    get() = NelePropertyType.BOOLEAN

  override val isReference: Boolean
    get() = false

  override var value: String?
    get() = if (flags.isFlagSet(this)) SdkConstants.VALUE_TRUE else SdkConstants.VALUE_FALSE
    set(value) {
      val on = value?.equals(SdkConstants.VALUE_TRUE, true) == true
      flags.setFlag(this, on)
    }

  val effectiveValue: Boolean
    get() = if (maskValue == 0) flags.maskValue == 0 else maskValue and flags.maskValue == maskValue
}
