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
package com.android.tools.idea.common.property2.impl.model

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.support.ActionEnumValue
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

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

  init {
    updateValueFromProperty()
  }

  override var text: String = ""

  override fun validate(editedValue: String): String {
    return property.validate(editedValue)
  }

  fun enterKeyPressed(editedValue: String) {
    blockUpdates = true
    value = editedValue
    isPopupVisible = false
    super.enterKeyPressed()
    blockUpdates = false
  }

  fun escapeKeyPressed() {
    blockUpdates = true
    updateValueFromProperty()
    isPopupVisible = false
    blockUpdates = false
  }

  fun popupMenuWillBecomeVisible() {
    selectedItem = value
    _popupVisible = true
  }

  fun popupMenuWillBecomeInvisible(ignoreChanges: Boolean) {
    val newValue = selectedValue
    if (!ignoreChanges && newValue != null) {
      value = newValue.value
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
    listListeners.forEach { it.contentsChanged(event) }
  }
}
