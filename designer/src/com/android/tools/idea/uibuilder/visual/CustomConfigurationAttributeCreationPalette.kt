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
package com.android.tools.idea.uibuilder.visual

import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.UiMode
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.createFilter
import com.android.tools.idea.configurations.getFrameworkThemeNames
import com.android.tools.idea.configurations.getProjectThemeNames
import com.android.tools.idea.configurations.getRecommendedThemeNames
import com.android.tools.idea.configurations.groupDevices
import com.android.tools.idea.editors.theme.ThemeResolver
import com.android.ide.common.resources.Locale
import com.intellij.psi.PsiFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.DefaultFocusTraversalPolicy
import java.awt.GridLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.border.Border
import javax.swing.event.DocumentEvent

private const val PALETTE_TITLE = "ADD NEW CONFIGURATION"

private const val DEFAULT_CUSTOM_PREVIEW_NAME = "Preview"

private const val HORIZONTAL_BORDER = 12
private const val FIELD_VERTICAL_BORDER = 3

/**
 * The panel for creating a [CustomConfigurationAttribute]. When a [CustomConfigurationAttribute] is created the
 * [createdCallback] is triggered.
 */
class CustomConfigurationAttributeCreationPalette(private val file: PsiFile,
                                                  private val facet: AndroidFacet,
                                                  private val createdCallback: (CustomConfigurationAttribute) -> Unit)
  : AdtPrimaryPanel(BorderLayout()) {

  private var configurationName: String = DEFAULT_CUSTOM_PREVIEW_NAME
  private var selectedDevice: Device? = null
  private var selectedApiTarget: IAndroidTarget? = null
  private var selectedOrientation: ScreenOrientation? = null
  private var selectedLocale: Locale? = null
  private var selectedTheme: String? = null
  private var selectedUiMode: UiMode? = null
  private var selectedNightMode: NightMode? = null

  private var defaultFocusComponent: JComponent? = null

  private val createAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      createdCallback(CustomConfigurationAttribute(configurationName,
                                                   selectedDevice?.id,
                                                   selectedApiTarget?.version?.apiLevel,
                                                   selectedOrientation,
                                                   selectedLocale?.toString(),
                                                   selectedTheme,
                                                   selectedUiMode,
                                                   selectedNightMode))
    }
  }

  init {
    add(createHeader(), BorderLayout.NORTH)

    val optionPanel = AdtPrimaryPanel()
    optionPanel.preferredSize = JBDimension(280, 280)
    optionPanel.layout = GridLayout(8, 2)
    optionPanel.border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER, 0, 0, 0)
    add(optionPanel, BorderLayout.CENTER)

    optionPanel.add(JBLabel("Name").apply { border = createFieldNameBorder() })
    optionPanel.add(createNameOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("Device").apply { border = createFieldNameBorder() })
    optionPanel.add(createDeviceOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("API").apply { border = createFieldNameBorder() })
    optionPanel.add(createApiOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("Orientation").apply { border = createFieldNameBorder() })
    optionPanel.add(createOrientationOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("Language").apply { border = createFieldNameBorder() })
    optionPanel.add(createLocaleOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("Theme").apply { border = createFieldNameBorder() })
    optionPanel.add(createThemeOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("UI Mode").apply { border = createFieldNameBorder() })
    optionPanel.add(createUiModeOptionPanel().apply { border = createFieldComponentBorder() })

    optionPanel.add(JBLabel("Night Mode").apply { border = createFieldNameBorder() })
    optionPanel.add(createNightModeOptionPanel().apply { border = createFieldComponentBorder() })

    add(createAddButtonPanel(), BorderLayout.SOUTH)

    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = DefaultFocusTraversalPolicy()
  }

  private fun createHeader(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    val label = JBLabel(PALETTE_TITLE)
    label.border = JBUI.Borders.empty(HORIZONTAL_BORDER)
    panel.add(label, BorderLayout.CENTER)
    panel.add(JSeparator(), BorderLayout.SOUTH)
    return panel
  }

  private fun createNameOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    // It is okay to have duplicated name
    val editTextField = JBTextField(configurationName)
    editTextField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        configurationName = e.document.getText(0, e.document.length) ?: ""
      }
    })

    editTextField.isFocusable = true
    panel.add(editTextField, BorderLayout.CENTER)

    defaultFocusComponent = editTextField

    return panel
  }

  private fun createDeviceOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())

    val groupedDevices = groupDevices(ConfigurationManager.getOrCreateInstance(facet.module).devices.filter { !it.isDeprecated })
    val devices = groupedDevices.toSortedMap(Comparator { d1, d2 -> d1.orderOfOption - d2.orderOfOption }).flatMap { it.value }
    val boxModel = MyComboBoxModel(devices, { it.displayName })
    val box = CommonComboBox(boxModel)
    box.addActionListener { selectedDevice = boxModel.selectedValue }
    selectedDevice = boxModel.selectedValue

    box.isFocusable = true
    panel.add(box, BorderLayout.CENTER)

    return panel
  }

  private fun createApiOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    val apiLevels = ConfigurationManager.getOrCreateInstance(facet.module).targets.reversed()
    if (apiLevels.isEmpty()) {
      val noApiLevelLabel = JBLabel("No available API Level")
     panel.add(noApiLevelLabel, BorderLayout.CENTER)
    }
    else {
      val boxModel = MyComboBoxModel<IAndroidTarget>(apiLevels, { it.version.apiLevel.toString() })
      val box = CommonComboBox(boxModel)
      box.addActionListener { selectedApiTarget = boxModel.selectedValue }
      selectedApiTarget = boxModel.selectedValue

      box.isFocusable = true
      panel.add(box, BorderLayout.CENTER)
    }

    return panel
  }

  private fun createOrientationOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())

    val boxModel = MyComboBoxModel(ScreenOrientation.values().toList(), { it.name })
    val box = CommonComboBox(boxModel)
    box.addActionListener { selectedOrientation = boxModel.selectedValue }
    selectedOrientation = boxModel.selectedValue

    box.isFocusable = true
    panel.add(box, BorderLayout.CENTER)

    return panel
  }

  private fun createLocaleOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())

    val locales = listOf(null) + ConfigurationManager.getOrCreateInstance(facet.module).localesInProject
    val boxModel = MyComboBoxModel(locales,
                                   { it?.toLocaleId() ?: Locale.getLocaleLabel(it, false) },
                                   { Locale.getLocaleLabel(it, false)!!} )
    val box = CommonComboBox(boxModel)
    box.addActionListener { selectedLocale = boxModel.selectedValue }
    selectedLocale = boxModel.selectedValue

    box.isFocusable = true
    panel.add(box, BorderLayout.CENTER)

    return panel
  }

  private fun createThemeOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())

    val themeResolver = ThemeResolver(
      ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(file.virtualFile))
    val filter = createFilter(themeResolver, emptySet())

    val projectTheme = getProjectThemeNames(themeResolver, filter)
    val recommendedThemes = getRecommendedThemeNames(themeResolver, filter)
    val frameworkTheme = getFrameworkThemeNames(themeResolver, filter)
    val allThemes = projectTheme + recommendedThemes + frameworkTheme

    val boxModel = MyComboBoxModel(allThemes, { it })
    val box = CommonComboBox(boxModel)
    box.addActionListener { selectedTheme = boxModel.selectedValue }
    selectedTheme = boxModel.selectedValue

    box.isFocusable = true
    panel.add(box, BorderLayout.CENTER)

    return panel
  }

  private fun createUiModeOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    val legalUiModes = UiMode.values().filter { (selectedApiTarget?.version?.apiLevel ?: 1) >= it.since() }
    val modes = if (legalUiModes.isEmpty()) listOf(UiMode.NORMAL) else legalUiModes

    val boxModel = MyComboBoxModel(modes, { it.longDisplayValue!! })
    val box = CommonComboBox(boxModel)
    box.addActionListener { selectedUiMode = boxModel.selectedValue }
    selectedUiMode = boxModel.selectedValue

    box.isFocusable = true
    panel.add(box, BorderLayout.CENTER)

    return panel
  }

  private fun createNightModeOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    val modes = NightMode.values().toList()

    val boxModel = MyComboBoxModel(modes, { it.longDisplayValue!! })
    val box = CommonComboBox(boxModel)
    box.addActionListener { selectedNightMode = boxModel.selectedValue }
    selectedNightMode = boxModel.selectedValue

    box.isFocusable = true
    panel.add(box, BorderLayout.CENTER)

    return panel
  }

  private fun createFieldNameBorder(): Border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER, HORIZONTAL_BORDER, FIELD_VERTICAL_BORDER, 0)

  private fun createFieldComponentBorder(): Border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER, 0, FIELD_VERTICAL_BORDER, HORIZONTAL_BORDER)

  private fun createAddButtonPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER, 50, FIELD_VERTICAL_BORDER * 3, 50)
    val addButton = JButton(createAction)

    addButton.text = "Add"

    addButton.isFocusable = true
    panel.add(addButton, BorderLayout.CENTER)

    return panel
  }

  override fun requestFocusInWindow(): Boolean {
    return defaultFocusComponent?.requestFocusInWindow() ?: false
  }

  override fun addNotify() {
    super.addNotify()
    requestFocusInWindow()
  }
}

@Suppress("UNCHECKED_CAST")
private class MyComboBoxModel<T>(items: List<T>, selectedNameFunc: (T) -> String, optionNameFunc: (T) -> String = selectedNameFunc)
  : DefaultComboBoxModel<MyBoxItemWrapper<T>>(), CommonComboBoxModel<MyBoxItemWrapper<T>> {
  init {
    items.forEach { addElement(MyBoxItemWrapper(it, optionNameFunc)) }
  }

  val selectedValue: T?
    get() = (selectedItem as? MyBoxItemWrapper<T>)?.item

  override var value = selectedNameFunc((selectedItem as MyBoxItemWrapper<T>).item)

  override var text = selectedNameFunc((selectedItem as MyBoxItemWrapper<T>).item)

  override var editable = false
    private set

  override fun addListener(listener: ValueChangedListener) = Unit

  override fun removeListener(listener: ValueChangedListener) = Unit
}

/**
 * Wrapper the given item to have better display name.
 */
private class MyBoxItemWrapper<T>(val item: T, private val optionNameFunc: (T) -> String) {
  override fun toString(): String = optionNameFunc(item)
}

// The order of options in device dropdown button.
private val DeviceGroup?.orderOfOption: Int
  get() = when(this) {
    DeviceGroup.NEXUS_XL -> 0
    DeviceGroup.NEXUS_TABLET -> 1
    DeviceGroup.WEAR -> 2
    DeviceGroup.TV -> 3
    DeviceGroup.AUTOMOTIVE -> 4
    DeviceGroup.GENERIC -> 5
    DeviceGroup.NEXUS -> 6
    DeviceGroup.OTHER -> 7
    else -> 8
  }
