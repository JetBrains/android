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
package com.android.tools.property.panel.impl.model

import com.android.tools.property.panel.api.FlagsPropertyItem
import com.google.common.base.Joiner
import com.intellij.ui.SpeedSearchComparator
import icons.StudioIcons
import javax.swing.Icon

/**
 * Model for a popup with checkboxes for editing a [FlagsPropertyItem] ie. a property with flags.
 *
 * The logic here is complicated by:
 *  - each flag has a maskValue: an integer bit mask representing its value
 *  - the maskValue of a flag may be contained in a different flags maskValue
 *  - there may be a flag value that contains all the flags used by all flag values
 *  - there may be a flag value that represent the maskValue of 0 (zero)
 *  - the property may have a default value such that removing all flags will not result in a 0 maskValue
 *
 *  In addition the logic in this model will not change the property before [applyChanges]
 *  is called. This means that this model must keep state for a value that is different
 *  from the property value.
 */
class FlagPropertyEditorModel(private val flagsProperty: FlagsPropertyItem<*>) :
  TextFieldWithLeftButtonEditorModel(flagsProperty, false) {

  /**
   * Holds current the value of the property.
   *
   * This value is used to determine when the initial setting are up to date in [initDialogState].
   */
  private var initialValue: String? = null

  /** Holds the names of the flags that are currently set in the value of the property */
  private val initialSelectedItems = mutableSetOf<String>()

  /** Holds the names of the flags that are will be set by [applyChanges] */
  private val selectedItems = mutableSetOf<String>()

  /** Holds the computed mask value of the flags in [selectedItems] */
  private var maskValue = 0

  /** Holds the computed mask value of all possible flags in the [FlagsPropertyItem] */
  private var maskAll = 0

  /** Holds the name of a flag that represent a maskValue of 0 (if present) */
  private var zeroValue: String? = null

  private val filterComparator = SpeedSearchComparator()

  /** Returns the names of the flags currently set in the property in order */
  val initialItemsAboveSeparator: List<String>
    get() {
      initDialogState()
      return flagsProperty.children.map { it.name }.filter { initialSelectedItems.contains(it) }
    }

  /** Returns the names of the flags currently unset in the property in order */
  val initialItemsBelowSeparator: List<String>
    get() {
      initDialogState()
      return flagsProperty.children.map { it.name }.filterNot { initialSelectedItems.contains(it) }
    }

  /** Returns true if there are visible flags (after filtering) that are both set and unset */
  val flagDividerVisible: Boolean
    get() {
      if (filter.isEmpty()) {
        return initialSelectedItems.isNotEmpty() && flagsProperty.children.size > initialSelectedItems.size
      }
      return !(initialSelectedItems.none { isMatch(it) } || initialItemsBelowSeparator.none { isMatch(it) })
    }

  override val leftButtonIcon: Icon? =
    StudioIcons.LayoutEditor.Properties.FLAG

  /** Returns true if a named flag is currently set */
  fun isSelected(item: String): Boolean {
    val flag = flagsProperty.flag(item) ?: return false
    if (flag.maskValue == 0) {
      return selectedItems.contains(item)
    }
    return (flag.maskValue and maskValue) == flag.maskValue
  }

  /** Returns true if a named flag is not currently set but effectively set by another flag */
  fun isEnabled(item: String): Boolean {
    val flag = flagsProperty.flag(item) ?: return false
    if (flag.maskValue == 0) {
      return maskValue == 0
    }
    if (maskValue == 0 && zeroValue != null && selectedItems.contains(zeroValue!!)) {
      return false
    }
    return !isSelected(item) || selectedItems.contains(item)
  }

  /** Returns true if a named flag is currently visible due to filtering */
  fun isVisible(item: String): Boolean {
    return isMatch(item)
  }

  /** Should be called to (de)select a named flag */
  fun toggle(item: String) {
    if (!selectedItems.contains(item)) {
      selectedItems.add(item)
    } else {
      selectedItems.remove(item)
    }
    computeDialogState()
  }

  /**
   * Initialize variables for a flags dialog.
   *
   * Computes:
   * <ul>
   *   <li> maskAll   : All the flags bit values as one mask </li>
   *   <li> zeroValue : Which flag has no bits set </li>
   *   <li> initialSelectedItems : The names of the flags currently set as the value of the property </li>
   *   <li> selectedItems        : Reset to the same as initialSelectedItems </li>
   *   <li> maskValue            : The bit values of the flags currently set </li>
   * </ul>
   */
  private fun initDialogState() {
    val current = property.resolvedValue.orEmpty()
    if (initialValue == current && selectedItems == initialSelectedItems) {
      // Up to date, nothing to do.
      return
    }
    initialValue = current
    maskAll = 0
    zeroValue = null
    initialSelectedItems.clear()
    for (flag in flagsProperty.children) {
      maskAll = maskAll or flag.maskValue
      if (flag.actualValue) {
        initialSelectedItems.add(flag.name)
      }
      if (flag.maskValue == 0) {
        zeroValue = flag.name
      }
    }
    selectedItems.clear()
    selectedItems.addAll(initialSelectedItems)
    computeDialogState()
  }

  /** Apply the current selected flags as a new value of the property */
  fun applyChanges() {
    val list = flagsProperty.children.map { it.name }.filter { selectedItems.contains(it) }
    value = Joiner.on("|").join(list)
  }

  /** Select all possible bits in the mask. This may be just 1 flag or may be all non zero value flags */
  fun selectAll() {
    if (filter.isEmpty()) {
      selectedItems.clear()
      val flag = flagsProperty.children.firstOrNull { it.maskValue == maskAll }
      if (flag != null) {
        selectedItems.add(flag.name)
      }
      else {
        flagsProperty.children.filter { it.maskValue != 0 }.forEach { selectedItems.add(it.name) }
      }
    }
    else {
      flagsProperty.children.filter { isMatch(it.name) }.forEach { selectedItems.add(it.name) }
    }
    computeDialogState()
  }

  /**
   * Clear all possible bits in the mask.
   * If a default value is set use the zeroValue. Otherwise just remove all flags.
   */
  fun clearAll() {
    if (filter.isEmpty()) {
      selectedItems.clear()
    }
    else {
      selectedItems.removeIf { isMatch(it) }
      filter = ""
    }
    if (selectedItems.isEmpty()) {
      val zeroValue = zeroValue
      if (zeroValue != null) {
        selectedItems.add(zeroValue)
      }
    }
    computeDialogState()
  }

  /** The value used to filter the visible flags */
  var filter: String = ""
    set(value) {
      field = value
      fireValueChanged()
    }

  private fun isMatch(value: String): Boolean {
    return filter.isEmpty() || filterComparator.matchingFragments(filter, value) != null
  }

  /**
   * This method should be called initially and after each change of a flag.
   * The current values are updated and the editor is notified about changes.
   */
  private fun computeDialogState() {
    maskValue = 0
    selectedItems.forEach { maskValue = maskValue or (flagsProperty.flag(it)?.maskValue ?: 0) }
    fireValueChanged()
  }
}
