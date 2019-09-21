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

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.property.panel.api.ActionEnumValue
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.properties.Delegates

/**
 * Model of a ComboBox control for editing a property.
 *
 * The ComboBox editor may be used as a DropDown control by specifying `editable=false`.
 * Values can still be changed using the popup, but there will be no text field for typing a new value.
 * @property enumSupport The mechanism for controlling the items shown in the popup.
 * @property editable True if the value is editable with a text editor (ComboBox) or false (DropDown).
 * @property isPopupVisible Controls the visibility of the popup in the ComboBox / DropDown.
 */
class ComboBoxPropertyEditorModel(property: PropertyItem, private val enumSupport: EnumSupport, override val editable: Boolean) :
  BasePropertyEditorModel(property), CommonComboBoxModel<EnumValue> {

  private var selectedValue: EnumValue? = null
  private val listListeners = mutableListOf<ListDataListener>()

  private var _popupVisible = false
  var isPopupVisible: Boolean
    get() = _popupVisible
    set(value) {
      _popupVisible = value
      fireValueChanged()
    }

  /**
   * A property change is pending.
   *
   * Indicates if a change to the property value was initiated, but the value wasn't
   * immediately registered by the property. Use this value to omit change requests
   * generated from [focusLost].
   */
  private var pendingValueChange = false
  private var pendingValue: String? = null

  override var text by Delegates.observable(property.value.orEmpty()) { _, _, _ -> resetPendingValue() }

  private fun setPendingValue(newValue: String?) {
    pendingValueChange = true
    pendingValue = newValue
  }

  private fun resetPendingValue() {
    pendingValueChange = false
    pendingValue = null
  }

  override fun updateValueFromProperty() {
    text = value
    resetPendingValue()
  }

  override fun focusLost() {
    super.focusLost()
    commitChange()
  }

  override val editingSupport: EditingSupport
    get() = property.editingSupport

  override val placeHolderValue: String
    get() = property.defaultValue ?: ""

  fun enterKeyPressed() {
    blockUpdates = true
    try {
      isPopupVisible = false
      commitChange()
    }
    finally {
      blockUpdates = false
    }
  }

  fun escapeKeyPressed() {
    cancelEditing()
  }

  /**
   * Commit the current changed text.
   *
   * Return true if the change was successfully updated,
   * false if the value is same as before or a pending update is expected.
   */
  private fun commitChange(): Boolean {
    if (pendingValueChange && text == pendingValue) {
      return false
    }
    resetPendingValue()
    if (value == text) {
      return false
    }
    value = text
    return true
  }

  override fun cancelEditing(): Boolean {
    if (!isPopupVisible) {
      return super.cancelEditing()
    }
    blockUpdates = true
    updateValueFromProperty()
    isPopupVisible = false
    blockUpdates = false
    return false
  }

  fun popupMenuWillBecomeVisible() {
    selectedItem = value
    _popupVisible = true
  }

  fun popupMenuWillBecomeInvisible(ignoreChanges: Boolean) {
    val newValue = selectedValue
    if (!ignoreChanges && newValue != null) {

      // Be aware that we may loose focus on the next line,
      // if the EnumValue is an action that displays a dialog.
      // This is why we set text=value just before calling select.
      if (newValue.select(property)) {
        text = newValue.value ?: ""
        setPendingValue(newValue.value)
      }
      fireValueChanged()
    }
    _popupVisible = false
  }

  override fun getSize(): Int {
    return enumSupport.values.size
  }

  override fun getElementAt(index: Int): EnumValue? {
    return enumSupport.values.elementAt(index)
  }

  override fun addListDataListener(listener: ListDataListener) {
    listListeners.add(listener)
  }

  override fun removeListDataListener(listener: ListDataListener) {
    listListeners.remove(listener)
  }

  override fun setSelectedItem(item: Any?) {
    var newValue = item as? EnumValue
    if (newValue == null && item != null) {
      val strValue = item.toString()
      val enumValues = enumSupport.values
      for (enumValue in enumValues) {
        if (enumValue !is ActionEnumValue && strValue == enumValue.value) {
          newValue = enumValue
          break
        }
      }
    }
    selectedValue = newValue
    fireListDataChanged()
  }

  override fun getSelectedItem(): Any? {
    return selectedValue
  }

  private fun fireListDataChanged() {
    val event = ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, size)
    listListeners.toTypedArray().forEach { it.contentsChanged(event) }
  }
}
