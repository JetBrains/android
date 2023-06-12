/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.uibuilder.property.InputTypePropertyItem
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.BalloonImpl
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.util.application.invokeLater
import java.awt.DefaultFocusTraversalPolicy
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.ComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

// These 3 mask values comes from android/text/InputType.java:
private const val TYPE_MASK_CLASS = 0x0f
private const val TYPE_VARIATION_MASK = 0x0ff0
private const val TYPE_MASK_FLAGS = 0x0fff000

private const val TYPE_CLASS_AND_VARIATION_MASK = TYPE_MASK_CLASS.or(TYPE_VARIATION_MASK)

private const val TOP_BORDER_OF_FIRST_FLAG = 5
private const val FIXED_WIDTH = 200

/**
 * A custom editor for the android ATT_INPUT_TYPE property.
 *
 * We display:
 * - a type dropdown (there are 4: text, number, datetime, phone)
 * - a variant dropdown for text, number & datetime types
 * - flags for text and number types
 */
class InputTypeEditor(
  private val property: InputTypePropertyItem
): JPanel(GridBagLayout()) {
  @VisibleForTesting
  val typeModel = TypeModel()
  @VisibleForTesting
  val variationModel = VariationModel()
  @VisibleForTesting
  val flagsModel = FlagsModel()
  private val flagsPanel: JPanel
  private val type: ComboBox<String>
  var balloon: BalloonImpl? = null

  init {
    isOpaque = false
    background = UIUtil.TRANSPARENT_COLOR
    isFocusCycleRoot = true
    focusTraversalPolicy = DefaultFocusTraversalPolicy()

    val typeLabel = JBLabel("Type:").apply {
      font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }
    add(typeLabel, constraints(0, 0))
    type = ComboBoxWithFixedWidth(typeModel, FIXED_WIDTH).apply {
      // Make this a DropDown control:
      isEditable = false
      // A non editable JComboBox has focus issues. Among them: JDK-4110721.
      editor.editorComponent.isFocusable = false
      isFocusable = true
    }
    add(type, constraints(1, 0))
    val variationLabel = JBLabel("Variation:").apply {
      font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }
    typeLabel.preferredSize = variationLabel.preferredSize
    add(variationLabel, constraints(0, 1))
    val variation = ComboBoxWithFixedWidth(variationModel, FIXED_WIDTH).apply {
      // Make this a DropDown control:
      isEditable = false
      // A non editable JComboBox has focus issues. Among them: JDK-4110721.
      editor.editorComponent.isFocusable = false
      isFocusable = true
    }
    add(variation, constraints(1, 1))

    val flagsLabel = JBLabel("Flags:").apply {
      font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }
    add(flagsLabel, constraints(0, 2))
    flagsPanel = object : JPanel() {
      // Align the flagsLabel with the baseline of the first checkBox if any:
      override fun getBaseline(width: Int, height: Int): Int {
        if (componentCount == 0) {
          return -1
        }
        val first = getComponent(0)
        val size = first.preferredSize
        return first.getBaseline(size.width, size.height)
      }
    }
    flagsPanel.apply {
      isOpaque = false
      background = UIUtil.TRANSPARENT_COLOR
      layout = BoxLayout(flagsPanel, BoxLayout.Y_AXIS)
      isVisible = false
    }
    add(flagsPanel, constraints(1, 2))

    variationModel.addListDataListener(object : ListDataListener {
      override fun contentsChanged(event: ListDataEvent) {
        // Hide the variations dropdown and the label if this types doesn't have any variations:
        variation.isVisible = variationModel.size > 1
        variationLabel.isVisible = variationModel.size > 1
      }
      override fun intervalAdded(event: ListDataEvent) {}
      override fun intervalRemoved(event: ListDataEvent) {}
    })
    flagsModel.listeners.add(ValueChangedListener {
      // Recreate the check boxes after the type has changed:
      var first = true
      flagsPanel.removeAll()
      flagsModel.flags.forEach { flag ->
        flagsPanel.add(CheckBoxWithFixedWidth(flag.name, flagsModel.isFlagSet(flag), FIXED_WIDTH).apply {
          isOpaque = false
          background = UIUtil.TRANSPARENT_COLOR
          addItemListener { flagsModel.setFlag(flag, isSelected) }
          // Add a little top spacing for the 1st CheckBox in order to:
          //  - add a little room after the ComboBox above
          //  - make the 3 labels appear to have the same vertical padding
          border = if (first) JBUI.Borders.emptyTop(TOP_BORDER_OF_FIRST_FLAG) else null
          first = false
        })
      }
      flagsLabel.isVisible = flagsModel.flags.isNotEmpty()
      flagsPanel.isVisible = flagsLabel.isVisible
      flagsPanel.invalidate()
    })
    variationModel.updateModel()
    flagsModel.updateModel()
  }

  override fun requestFocus() {
    // When the popup is created we want focus on the type ComboBox.
    type.requestFocus()
  }

  private fun constraints(x: Int, y: Int): GridBagConstraints =
    GridBagConstraints().apply {
      gridx = x
      gridy = y
      fill = GridBagConstraints.HORIZONTAL
      anchor = GridBagConstraints.BASELINE
    }

  // Create the property value string such that the order of elements are predictable
  private fun updatePropertyValue() {
    val type = typeModel.selectedItem.toString()
    val variant = variationModel.selectedItem.toString()
    val builder = StringBuilder().append(type)
    if (variant.isNotEmpty() && variant != type) {
      builder.append("|").append(variant)
    }
    for (flag in flagsModel.flags) {
      if (flagsModel.isFlagSet(flag)) {
        builder.append("|").append(flag.name)
      }
    }
    property.value = builder.toString()
  }

  private fun addMaskedFlagsTo(mask: Int, out: MutableList<NlFlagPropertyItem>) {
    val typeMask = property.maskValue.and(TYPE_MASK_CLASS)
    if (typeMask != 0) {
      out.addAll(
        property.children.filter { it.maskValue.and(TYPE_MASK_CLASS) == typeMask && it.maskValue.and(mask) != 0 }.sortedBy { it.name }
      )
    }
  }

  private fun updateBalloonSize() {
    val balloon = balloon ?: return
    balloon.revalidate()
  }

  // We don't want the width of the popup to change when we change types.
  // Achieve this by making the controls in the 2nd column have a fixed width.
  private class ComboBoxWithFixedWidth<T>(model: ComboBoxModel<T>, private val fixedWidth: Int): ComboBox<T>(model) {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      return Dimension(JBUIScale.scale(fixedWidth), size.height)
    }
  }

  // We don't want the width of the popup to change when we change types.
  // Achieve this by making the controls in the 2nd column have a fixed width.
  private class CheckBoxWithFixedWidth(text: String, selected: Boolean, private val fixedWidth: Int): JCheckBox(text, selected) {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      val scaledWidth = JBUIScale.scale(fixedWidth)
      if (size.width > scaledWidth) {
        toolTipText = text
      }
      return Dimension(scaledWidth, size.height)
    }
  }

  /**
   * The possible types of InputType
   */
  inner class TypeModel : ComboBoxModel<String> {
    private val listeners = mutableListOf<ListDataListener>()
    private val types = listOf(
      "text",
      "number",
      "datetime",
      "phone"
    )
    private var selectedType = ""

    init {
      val type = property.maskValue.and(TYPE_MASK_CLASS)
      selectedType = types.firstOrNull { property.flag(it).maskValue == type } ?: ""
    }

    override fun getSize() = types.size

    override fun getElementAt(index: Int): String = types[index]

    override fun addListDataListener(listener: ListDataListener) {
      listeners.add(listener)
    }

    override fun removeListDataListener(listener: ListDataListener) {
      listeners.remove(listener)
    }

    override fun setSelectedItem(anItem: Any) {
      val newSelectedType = anItem.toString()
      if (newSelectedType == selectedType) {
        return
      }
      selectedType = anItem.toString()
      variationModel.reset()
      flagsModel.reset()

      updatePropertyValue()

      invokeLater {
        variationModel.updateModel()
        flagsModel.updateModel()
        invalidate()
        validate()
        updateBalloonSize()
      }
    }

    override fun getSelectedItem(): Any = selectedType
  }

  /**
   * The possible variations of a given type of InputType
   */
  inner class VariationModel : ComboBoxModel<String> {
    private val listeners = mutableListOf<ListDataListener>()
    private val variations = mutableListOf<NlFlagPropertyItem>()
    private var typeName = ""
    private var selectedVariant = ""

    fun reset() {
      typeName = ""
      selectedVariant = ""
      variations.clear()
    }

    fun updateModel() {
      typeName = typeModel.selectedItem.toString()
      selectedVariant = typeName
      if (typeName.isNotEmpty()) {
        val typeFlag = property.children.firstOrNull { it.name == typeName }
        val typeMask = typeFlag?.maskValue ?: 0
        if (typeFlag != null && typeMask != 0) {
          variations.add(typeFlag)
          addMaskedFlagsTo(TYPE_VARIATION_MASK, variations)
          val current = property.maskValue.and(TYPE_CLASS_AND_VARIATION_MASK)
          val currentFlag = property.children.firstOrNull { it.maskValue == current }
          selectedVariant = if (current == 0) typeName else currentFlag?.name ?: typeName
        }
      }
      notifyListeners()
    }

    private fun notifyListeners() {
      val event = ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, size)
      listeners.toTypedArray().forEach { it.contentsChanged(event) }
    }

    override fun getSize() = variations.size

    override fun getElementAt(index: Int): String = variations[index].name

    override fun addListDataListener(listener: ListDataListener) {
      listeners.add(listener)
    }

    override fun removeListDataListener(listener: ListDataListener) {
      listeners.remove(listener)
    }

    override fun setSelectedItem(anItem: Any) {
      selectedVariant = anItem.toString()
      updatePropertyValue()
    }

    override fun getSelectedItem(): Any = selectedVariant
  }

  /**
   * The possible flags for a given type of InputType
   */
  inner class FlagsModel {
    private val settings = mutableMapOf<String, Boolean>()
    val flags = mutableListOf<NlFlagPropertyItem>()
    val listeners = mutableListOf<ValueChangedListener>()

    fun reset() {
      flags.clear()
      settings.clear()
    }

    fun updateModel() {
      addMaskedFlagsTo(TYPE_MASK_FLAGS, flags)
      flags.forEach { settings[it.name] = property.isFlagSet(it) }
      notifyListeners()
    }

    fun isFlagSet(flag: NlFlagPropertyItem): Boolean =
      settings[flag.name] ?: false

    fun setFlag(flag: NlFlagPropertyItem, selected: Boolean) {
      settings[flag.name] = selected
      updatePropertyValue()
    }

    private fun notifyListeners() {
      listeners.toTypedArray().forEach { it.valueChanged() }
    }
  }
}
