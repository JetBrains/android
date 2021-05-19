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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.property.panel.api.ActionEnumValue
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Future
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
class ComboBoxPropertyEditorModel(
  property: PropertyItem,
  private val enumSupport: EnumSupport,
  override val editable: Boolean
) : BasePropertyEditorModel(property), CommonComboBoxModel<EnumValue> {
  /** Object for synchronizing access to [newValues] */
  private val syncNewValues = Object()
  private val loading = mutableListOf(EnumValue.LOADING)
  private var values: List<EnumValue> = loading
  @GuardedBy("syncNewValues")
  private var newValues: List<EnumValue> = loading
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

  override var property: PropertyItem
    get() = super.property
    set(value) {
      super.property = value
      // Without this the outline validations are wrong in the EditorBasedTableCellRenderer
      updateValueFromProperty()
    }

  override var text by Delegates.observable(property.value.orEmpty()) { _, _, _ -> resetPendingValue() }

  init {
    if (!editable) {
      setInitialDropDownValue(value)
    }
  }

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
    if (!editable) {
      setInitialDropDownValue(value)
    }
    resetPendingValue()
  }

  private fun setInitialDropDownValue(stringValue: String) {
    loading.clear()
    if (stringValue.isNotEmpty()) {
      val newValue = enumSupport.createValue(stringValue)
      selectedValue = newValue
      loading.add(newValue)
    }
    loading.add(EnumValue.LOADING)
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

  fun popupMenuWillBecomeVisible(updatePopup: () -> Unit): Future<*> {
    _popupVisible = true
    var result: Future<*> = Futures.immediateFuture(null)
    if (values === loading) {
      result = editingSupport.execution(Runnable {
        // The call to enumSupport.values may be slow.
        // Call it from a non UI thread:
        val newEnumValues = enumSupport.values
        synchronized(syncNewValues) {
          // The "newValues" property is accessed from multiple threads.
          // Make the update inside a synchronized section.
          newValues = newEnumValues

          // Notify the UI thread that newValues has been updated.
          syncNewValues.notify()
        }
        editingSupport.uiExecution(Runnable {
          if (values === loading) {
            // New values have been loaded but the list model has not been updated.
            synchronized(syncNewValues) {
              values = newValues
              selectedItem = value
            }
            // Update the data in the list of the popup.
            fireListDataInserted()

            // Notify the UI that there are new items in the list.
            updatePopup()
          }
        })
      })
      if (values === loading) {
        synchronized(syncNewValues) {
          // To avoid flickering from quick enumSupport.values call:
          // check if the values are ready after a small delay.
          if (newValues === loading) {
            syncNewValues.wait(100L)
          }
          if (values === loading && newValues !== loading) {
            values = newValues
            selectedItem = value
          }
        }
      }
    }
    return result
  }

  fun popupMenuWillBecomeInvisible() {
    _popupVisible = false
  }

  fun selectEnumValue() {
    val newValue = selectedValue
    if (newValue != null) {

      // Be aware that we may loose focus on the next line,
      // if the EnumValue is an action that displays a dialog.
      // This is why we set text=value just before calling select.
      if (newValue.select(property)) {
        text = newValue.value ?: ""
        setPendingValue(newValue.value)
      }
      fireValueChanged()
    }
  }

  override fun getSize(): Int {
    return values.size
  }

  override fun getElementAt(index: Int): EnumValue? {
    return values.elementAt(index)
  }

  override fun addListDataListener(listener: ListDataListener) {
    listListeners.add(listener)
  }

  override fun removeListDataListener(listener: ListDataListener) {
    listListeners.remove(listener)
  }

  override fun setSelectedItem(item: Any?) {
    if (values === loading) {
      return
    }
    var newValue = item as? EnumValue
    if (newValue == null && item != null) {
      val strValue = item.toString()
      for (enumValue in values) {
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

  private fun fireListDataInserted() {
    val event = ListDataEvent(this, ListDataEvent.INTERVAL_ADDED, 0, size)
    listListeners.toTypedArray().forEach { it.contentsChanged(event) }
  }
}
