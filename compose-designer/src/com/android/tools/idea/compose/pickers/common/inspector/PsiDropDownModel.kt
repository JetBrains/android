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
package com.android.tools.idea.compose.pickers.common.inspector

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Future
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * Model for the PsiPropertyDropdown component.
 *
 * Manages how values are loaded and selected into the dropdown popup menu.
 */
internal class PsiDropDownModel(property: PsiPropertyItem, private val enumSupport: EnumSupport) :
  BasePropertyEditorModel(property), CommonComboBoxModel<EnumValue> {
  private val syncNewValues = Object()

  /**
   * Provisional list used before values are loaded from [enumSupport]. Used to have a Loading
   * indicator if the loading takes too long.
   */
  private val loading = mutableListOf(EnumValue.LOADING)

  /** Reference to the current list of [EnumValue], this is the one reflected in the DropDown. */
  private var values: List<EnumValue> = loading

  /**
   * Provisional list reference to safely handle the new set of values loaded from [enumSupport].
   */
  @GuardedBy("syncNewValues") private var newValues: List<EnumValue> = loading
  private var selectedValue: EnumValue? = null
  private val listListeners = mutableListOf<ListDataListener>()

  override val editable: Boolean = false

  override var property: PropertyItem
    get() = super.property
    set(value) {
      super.property = value
      // Without this the outline validations are wrong in the EditorBasedTableCellRenderer
      updateValueFromProperty()
    }

  override var text = "" // We don't care about the text

  override val editingSupport: EditingSupport
    get() = property.editingSupport

  override val placeHolderValue: String
    get() = property.defaultValue ?: ""

  init {
    setInitialDropDownValue()
  }

  override fun updateValueFromProperty() {
    val currentIndex = getIndexOfCurrentValue()
    if (currentIndex >= 0) {
      selectedItem = getElementAt(currentIndex)
    } else {
      setInitialDropDownValue()
    }
  }

  fun getIndexOfCurrentValue(): Int {
    val stringValue = value
    if (stringValue.isNotEmpty()) {
      for (index in 0 until size) {
        if (getElementAt(index)?.value == stringValue) {
          return index
        }
      }
    }
    return -1
  }

  private fun setInitialDropDownValue() {
    loading.clear()

    val currentValueString = value
    val defaultValueString = property.defaultValue.orEmpty()
    val stringToUse = currentValueString.ifEmpty { defaultValueString }
    val valueHandler: (String) -> EnumValue =
      if (currentValueString.isNotEmpty()) {
        { enumSupport.createValue(it) }
      } else {
        // For default values we should use an EnumValue that assigns null
        { EnumValue.empty(it) }
      }

    if (stringToUse.isNotEmpty()) {
      val newValue = valueHandler(stringToUse)
      selectedValue = newValue
      loading.add(newValue)
    }
    loading.add(EnumValue.LOADING)
  }

  override fun cancelEditing(): Boolean = true

  /**
   * Call to indicate the model that it should load the values from [enumSupport], considers that
   * the values might take a noticeable long time to populate, in which case [updatePopup] will be
   * called to indicate the Component that there are new values on the list, and it should update
   * the contents of the popup.
   */
  fun popupMenuWillBecomeVisible(updatePopup: () -> Unit): Future<*> {
    var result: Future<*> = Futures.immediateFuture(null)
    if (values === loading) {
      result =
        editingSupport.execution(
          Runnable {
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
            editingSupport.uiExecution(
              Runnable {
                if (values === loading) {
                  // New values have been loaded but the list model has not been updated.
                  synchronized(syncNewValues) { values = newValues }
                  // Update the data in the list of the popup.
                  fireListDataInserted()

                  // Notify the UI that there are new items in the list.
                  updatePopup()
                }
              }
            )
          }
        )
      if (values === loading) {
        synchronized(syncNewValues) {
          // To avoid flickering from quick enumSupport.values call:
          // check if the values are ready after a small delay.
          if (newValues === loading) {
            syncNewValues.wait(100L)
          }
          if (values === loading && newValues !== loading) {
            values = newValues
          }
        }
      }
    }
    return result
  }

  fun selectEnumValue() {
    val newValue = selectedValue
    if (newValue != null) {
      newValue.select(property)
      fireValueChanged()
    }
  }

  override fun getSize(): Int {
    return values.size
  }

  override fun getElementAt(index: Int): EnumValue? {
    return values.elementAtOrNull(index)
  }

  override fun addListDataListener(listener: ListDataListener) {
    listListeners.add(listener)
  }

  override fun removeListDataListener(listener: ListDataListener) {
    listListeners.remove(listener)
  }

  override fun setSelectedItem(item: Any?) {
    if (values === loading) {
      // The values have not loaded yet, so we should avoid changing the item set by
      // 'setInitialDropDownValue', this might happen from
      // internal classes making assumptions, but this condition won't trigger if it's the user that
      // selects the item
      return
    }
    val newValue = item as? EnumValue
    if (selectedValue?.value != newValue?.value) {
      selectedValue = newValue
      fireListDataChanged()
    }
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
