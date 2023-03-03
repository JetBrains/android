/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.qualifiers

import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.idea.ui.resourcemanager.CollectionParam
import com.android.tools.idea.ui.resourcemanager.InputParam
import com.android.tools.idea.ui.resourcemanager.IntParam
import com.android.tools.idea.ui.resourcemanager.TextParam
import com.android.tools.idea.ui.resourcemanager.bind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ItemEvent
import java.util.Observable
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.PopupMenuEvent

private val QUALIFIER_TYPE_COMBO_SIZE = JBUI.size(370, 30)
private val QUALIFIER_VALUE_COMBO_SIZE = QUALIFIER_TYPE_COMBO_SIZE.withWidth(400)
private val FLOW_LAYOUT_GAP = JBUI.scale(4)
private val ADD_BUTTON_BORDER = JBUI.Borders.empty(4, 8, 4, 0)
private val ADD_BUTTON_SIZE = JBUI.size(20)

private const val CLEAR_QUALIFIER_DESC = "Clear qualifier"

/**
 * View to manipulate the [QualifierConfigurationViewModel]. It represents the qualifiers that will be
 * add to a resource.
 */
class QualifierConfigurationPanel(private val viewModel: QualifierConfigurationViewModel) : JPanel(
  BorderLayout(1, 0)) {

  private val configurationChanged: (Observable, Any?) -> Unit = { _, _ -> viewModel.applyConfiguration() }

  private val qualifierContainer = JPanel(VerticalLayout(0, SwingConstants.LEFT))

  private val addQualifierButton = LinkLabel("Add another qualifier", null, ::onAddQualifierLabelClicked)
    .also { label ->
      label.border = ADD_BUTTON_BORDER
      label.isEnabled = canAddConfigurationRow()
      label.isFocusable = true
    }

  private val qualifierTypeLabel = JBLabel("QUALIFIER TYPE").apply {
    preferredSize = QUALIFIER_TYPE_COMBO_SIZE
    font = JBUI.Fonts.smallFont()
  }

  private val qualifierValueLabel = JBLabel("VALUE").apply {
    border = JBUI.Borders.emptyLeft(FLOW_LAYOUT_GAP)
    font = JBUI.Fonts.smallFont()
  }

  init {
    val initialConfigurations = viewModel.getCurrentConfigurations()
    if (initialConfigurations.isEmpty()) {
      addConfigurationRow()
    }
    else {
      initialConfigurations
        .map { (qualifier, configuration) -> ConfigurationRow(viewModel, qualifier, configuration) }
        .forEach { qualifierContainer.add(it) }
    }
    addQualifierButton.isEnabled = canAddConfigurationRow()

    add(JPanel(FlowLayout(FlowLayout.LEFT, FLOW_LAYOUT_GAP, 0)).apply {
      add(qualifierTypeLabel)
      add(qualifierValueLabel)
    }, BorderLayout.NORTH)

    add(qualifierContainer)
    add(addQualifierButton, BorderLayout.SOUTH)
  }

  private fun onAddQualifierLabelClicked(
    label: LinkLabel<Any?>,
    @Suppress("UNUSED_PARAMETER") ignored: Any?
  ) {
    viewModel.applyConfiguration()
    addConfigurationRow()
    label.isEnabled = canAddConfigurationRow()
  }

  private fun addConfigurationRow() {
    val configurationRow = ConfigurationRow(viewModel)
    qualifierContainer.add(configurationRow)
    qualifierContainer.revalidate()
    qualifierContainer.repaint()
    configurationRow.qualifierCombo.requestFocus()
  }

  private fun canAddConfigurationRow(): Boolean =
    viewModel.canAddQualifier()
    && qualifierContainer.components
      .filterIsInstance<ConfigurationRow>()
      .map { it.qualifierCombo }
      .all { it.selectedIndex != -1 }

  private fun populateAvailableQualifiers(comboBox: ComboBox<*>) {
    var availableQualifiers = viewModel.getAvailableQualifiers().map { ResourceQualifierWrapper(it) }
    val selectedItem = comboBox.selectedItem as ResourceQualifierWrapper?
    if (selectedItem != null) {
      // Prepend the selected element which is not in the available qualifiers.
      availableQualifiers = listOf(selectedItem) + availableQualifiers
    }
    comboBox.model = CollectionComboBoxModel(availableQualifiers, selectedItem)
  }

  /**
   * A view showing a dropdown to choose a [ResourceQualifier] and the field to set its parameters.
   *
   * @param viewModel the existing instance of  [QualifierConfigurationViewModel] used in [QualifierConfigurationViewModel].
   * @param qualifier an optional [ResourceQualifier] to pre-populate the [ResourceQualifier] dropdown.
   * @param configuration an optional [QualifierConfiguration] to pre-populate the parameter of the [ResourceQualifier].
   */
  private inner class ConfigurationRow(val viewModel: QualifierConfigurationViewModel,
                                       qualifier: ResourceQualifier? = null,
                                       configuration: QualifierConfiguration? = null)
    : JPanel(FlowLayout(FlowLayout.LEFT, FLOW_LAYOUT_GAP, 0)) {


    private val valuePanel = JPanel(FlowLayout(FlowLayout.LEFT, FLOW_LAYOUT_GAP, 0))

    val qualifierCombo = ComboBox(arrayOf(ResourceQualifierWrapper(qualifier))).apply {
      renderer = getRenderer("Select a type", ResourceQualifierWrapper::toString)
      preferredSize = QUALIFIER_TYPE_COMBO_SIZE

      addPopupMenuListener(object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
          // Recreate the available qualifiers each time the popup is shown
          // because the available qualifier might have change if another
          // comboBox has had its value changed
          populateAvailableQualifiers(e.source as ComboBox<*>)
        }
      })

      addItemListener { itemEvent ->
        when (itemEvent.stateChange) {
          ItemEvent.DESELECTED -> (itemEvent.item as ResourceQualifierWrapper).qualifier?.let { viewModel.deselectQualifier(it) }
          ItemEvent.SELECTED -> (itemEvent.item as ResourceQualifierWrapper).qualifier?.let { updateValuePanel(viewModel.selectQualifier(it)) }
        }
        addQualifierButton.isEnabled = canAddConfigurationRow()
      }

      selectedItem = qualifier
    }

    private val deleteButton = createDeleteButton()

    init {
      add(qualifierCombo)
      add(valuePanel)
      add(deleteButton)
      updateValuePanel(configuration)
    }

    private fun createDeleteButton(): ActionButton {
      val action = object : DumbAwareAction(CLEAR_QUALIFIER_DESC, CLEAR_QUALIFIER_DESC, StudioIcons.Common.CLOSE) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
          (qualifierCombo.selectedItem as? ResourceQualifierWrapper)?.qualifier?.let { viewModel.deselectQualifier(it) }
          if (parent.componentCount > 1) deleteRow() else reset()
          addQualifierButton.isEnabled = canAddConfigurationRow()
        }
      }
      return ActionButton(action, action.templatePresentation.clone(), "Resource Explorer", ADD_BUTTON_SIZE).apply { isFocusable = true }
    }

    private fun deleteRow() {
      with(parent) {
        remove(this@ConfigurationRow)
        revalidate()
        repaint()
      }
    }

    private fun reset() {
      // If this is the last row in, we don't delete it, we just reset the fields.
      qualifierCombo.selectedIndex = -1
      updateValuePanel(null)
    }

    private fun updateValuePanel(qualifierConfiguration: QualifierConfiguration?) {
      valuePanel.removeAll()
      if (qualifierConfiguration != null) {
        addFieldsForParams(qualifierConfiguration)
      }
      else {
        valuePanel.add(createDefaultCombo())
      }
      revalidate()
      repaint()
    }

    private fun createDefaultCombo() = ComboBox<Any>().apply {
      isEnabled = false
      isEditable = false
      preferredSize = QUALIFIER_VALUE_COMBO_SIZE
    }

    /**
     * Adds the UI components corresponding to the provided [qualifierConfiguration] to [valuePanel]
     */
    private fun addFieldsForParams(qualifierConfiguration: QualifierConfiguration) {
      val fieldSize = computeFieldSize(qualifierConfiguration.parameters.size)
      qualifierConfiguration.parameters
        .mapNotNull<InputParam<*>, JComponent> { qualifierParam ->
          qualifierParam.addObserver(configurationChanged)
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
      (qualifierParam as CollectionParam<Any?>).bind(ComboBox<Any?>().apply {
        renderer = getRenderer(qualifierParam.placeholder, qualifierParam.parser)
        selectedItem = qualifierParam.paramValue
      })

    /**
     * Returns the dimension that each component should have so they all fit within [QUALIFIER_VALUE_COMBO_SIZE]
     */
    private fun computeFieldSize(paramNumber: Int) = Dimension(
      QUALIFIER_VALUE_COMBO_SIZE.width() / paramNumber - (FLOW_LAYOUT_GAP / 2 * (paramNumber - 1)),
      QUALIFIER_VALUE_COMBO_SIZE.height()
    )
  }

  /**
   * Returns a [ColoredListCellRenderer] that will try to use the provided [textRenderer] to format the list value into a String.
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

  /**
   * Class to wrap [ResourceQualifier] in order to override the [toString] method to return the qualifier name.
   * The [toString] method is what is used for keyboard navigation in the combo box, so it is important that it
   * matches the combo box renderer.
   */
  data class ResourceQualifierWrapper(val qualifier: ResourceQualifier?) {
    override fun toString() = qualifier?.name ?: ""
  }
}