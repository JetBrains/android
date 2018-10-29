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
package com.android.tools.idea.resourceExplorer.view

import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.resourceExplorer.CollectionParam
import com.android.tools.idea.resourceExplorer.InputParam
import com.android.tools.idea.resourceExplorer.IntParam
import com.android.tools.idea.resourceExplorer.TextParam
import com.android.tools.idea.resourceExplorer.bind
import com.android.tools.idea.resourceExplorer.viewmodel.QualifierConfiguration
import com.android.tools.idea.resourceExplorer.viewmodel.QualifierConfigurationViewModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

private val QUALIFIER_TYPE_COMBO_SIZE = JBUI.size(250, 30)
private val QUALIFIER_VALUE_COMBO_SIZE = JBUI.size(220, QUALIFIER_TYPE_COMBO_SIZE.height)
private val QUALIFIER_VALUE_GAP = JBUI.scale(4)

/**
 * View to manipulate the [QualifierConfigurationViewModel]. It represents the qualifiers that will be
 * add to a resource.
 */
class QualifierConfigurationPanel(private val viewModel: QualifierConfigurationViewModel) : JPanel(
  BorderLayout()) {

  private val addQualifierButton = ClickableLabel("Add another qualifier", StudioIcons.Common.ADD,
                                                  SwingConstants.LEFT).apply {
    addActionListener {
      if (viewModel.canAddQualifier()) {
        viewModel.applyConfiguration()
        addConfigurationRow()
      }
    }
  }

  private val qualifierTypeLabel = JBLabel("QUALIFIER TYPE").apply {
    preferredSize = QUALIFIER_TYPE_COMBO_SIZE
  }
  private val qualifierValueLabel = JBLabel("VALUE")
  private val qualifierContainer = JPanel(VerticalLayout(0, SwingConstants.LEFT))

  init {
    addConfigurationRow()
  }

  init {
    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(qualifierTypeLabel)
      add(qualifierValueLabel)
    }, BorderLayout.NORTH)

    add(qualifierContainer)
    add(addQualifierButton, BorderLayout.SOUTH)
  }

  private fun addConfigurationRow() {
    qualifierContainer.add(ConfigurationRow(viewModel))
    qualifierContainer.revalidate()
    qualifierContainer.repaint()
  }
}

private class ConfigurationRow(viewModel: QualifierConfigurationViewModel) : JPanel(
  FlowLayout(FlowLayout.LEFT)) {

  private val valuePanel = JPanel(FlowLayout(FlowLayout.LEFT, QUALIFIER_VALUE_GAP, 0)).apply {
    add(ComboBox<Any>().apply {
      isEnabled = false
      isEditable = false
      preferredSize = QUALIFIER_VALUE_COMBO_SIZE
    })
  }

  val qualifierCombo = ComboBox<ResourceQualifier>(
    CollectionComboBoxModel(viewModel.getAvailableQualifiers().sortedBy(ResourceQualifier::getName), null)).apply {
    renderer = getRenderer("Select a type", ResourceQualifier::getName)
    preferredSize = QUALIFIER_TYPE_COMBO_SIZE
    addActionListener { event ->
      val comboBox = event.source as ComboBox<*>
      val qualifier = comboBox.selectedItem as? ResourceQualifier ?: return@addActionListener
      updateValuePanel(viewModel.getQualifierConfiguration(qualifier))
    }
  }

  init {
    add(qualifierCombo)
    add(valuePanel)
    add(ClickableLabel("", StudioIcons.Common.CLOSE, SwingConstants.LEFT))
  }

  private fun updateValuePanel(qualifierConfiguration: QualifierConfiguration?) {
    valuePanel.removeAll()
    if (qualifierConfiguration != null) {
      addFieldsForParams(qualifierConfiguration)
    }
    revalidate()
    repaint()
  }

  /**
   * Adds the UI components corresponding to the provided [qualifierConfiguration] to [valuePanel]
   */
  private fun addFieldsForParams(qualifierConfiguration: QualifierConfiguration) {
    val fieldSize = computeFieldSize(qualifierConfiguration.parameters.size)
    qualifierConfiguration.parameters
      .mapNotNull<InputParam<*>, JComponent> { qualifierParam ->
        when (qualifierParam) {
          is CollectionParam<*> -> createComboBox(qualifierParam)
          is IntParam -> createIntegerField(qualifierParam)
          is TextParam -> createTextField(qualifierParam)
          else -> null
        }
      }
      .forEach {
        it.preferredSize = fieldSize
        valuePanel.add(it)
      }
  }

  private fun createTextField(qualifierParam: TextParam): JBTextField {
    val textField = JBTextField().apply {
      qualifierParam.placeholder?.let {
        setTextToTriggerEmptyTextStatus(it)
      }
    }
    qualifierParam.bind(textField.document)
    return textField
  }

  private fun createIntegerField(qualifierParam: IntParam): IntegerField {
    val field = IntegerField()
    qualifierParam.range?.let {
      field.minValue = it.start
      field.maxValue = it.endInclusive
    }
    qualifierParam.bind(field.document)
    return field
  }

  private fun createComboBox(qualifierParam: CollectionParam<*>) =
    (qualifierParam as CollectionParam<Any?>).bind(createComboBox(qualifierParam.placeholder, qualifierParam.parser))

  /**
   * Returns the dimension that each component should have so they all fit within [QUALIFIER_VALUE_COMBO_SIZE]
   */
  private fun computeFieldSize(paramNumber: Int) = Dimension(
    QUALIFIER_VALUE_COMBO_SIZE.width() / paramNumber - (QUALIFIER_VALUE_GAP / 2 * (paramNumber - 1)),
    QUALIFIER_VALUE_COMBO_SIZE.height()
  )

  private fun createComboBox(placeholder: String?, parser: ((Any?) -> String?)?) = ComboBox<Any?>().apply {
    renderer = getRenderer(placeholder, parser)
  }
}

/**
 * Return a [ColorResourceCellRenderer] that will try to use the provided [textRenderer] to format the list value into a String.
 * If a value of the list is null, [placeholderValue] will be used instead.
 */
private fun <T> getRenderer(placeholderValue: String?, textRenderer: ((T) -> String?)?) = object : ColoredListCellRenderer<T?>() {
  override fun customizeCellRenderer(list: JList<out T?>,
                                     value: T?,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    when (value) {
      null -> append(placeholderValue ?: "Select a value...", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
      else -> append(textRenderer?.let { it(value) } ?: value.toString())
    }
  }
}