/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.sdklib.devices.Storage
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.ComboBoxModel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

/**
 * Storage field for displaying and editing a [Storage] value
 */
class StorageField : JPanel() {
  private val DEFAULT_UNIT = Storage.Unit.MiB
  private val unitModel: ComboBoxModel<Storage.Unit> = EnumComboBoxModel(Storage.Unit::class.java)
  private val unitsCombo: ComboBox<Storage.Unit> = ComboBox(unitModel)
  private val valueField = JTextField()
  private var currentUnit = DEFAULT_UNIT
  private val storage: ObjectProperty<Storage> = ObjectValueProperty(Storage(0, DEFAULT_UNIT))

  fun storage(): ObjectProperty<Storage> = storage

  val preferredSizeOfUnitsDropdown: Dimension
    get() = unitsCombo.preferredSize

  private fun updateStorageField() {
    currentUnit = unitsCombo.selectedItem as Storage.Unit
    val newText = storage.get().getSizeAsUnit(currentUnit).toString()
    val oldText = valueField.text
    if (newText != oldText) {
      // This puts the cursor at the end. Don't call it unless it's necessary.
      valueField.text = newText
    }
  }

  private fun updateStorage() {
    val text = valueField.text
    if (text == null || text.isEmpty()) {
      storage.set(Storage(0, currentUnit))
      return
    }
    try {
      val newValue = text.toLong()
      storage.set(Storage(newValue, currentUnit))
    }
    catch (ex: NumberFormatException) {
      val oldValue = storage.get().getSizeAsUnit(currentUnit)
      valueField.text = oldValue.toString()
    }
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    unitsCombo.isEnabled = enabled
    valueField.isEnabled = enabled
  }

  init {
    layout = BorderLayout(3, 0)
    updateStorageField()
    add(valueField, BorderLayout.CENTER)
    add(unitsCombo, BorderLayout.EAST)

    unitsCombo.apply {
      selectedItem = DEFAULT_UNIT
      renderer = SimpleListCellRenderer.create("") { it.displayValue }
      addActionListener { updateStorageField() }
    }

    valueField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        // updateStorage might set myValueField as a side effect, which will cause this document
        // to throw an exception. Side-step that problem by invoking the call to happen later.
        invokeLater { updateStorage() }
      }
    })
    storage.addListener { updateStorageField() }
  }
}

