/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.ComboBoxEditor
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

private val minimumAndPreferredWidth
  get() = JBUI.scale(300)
/**
 * Dedicated [ComboBox] component to edit JDK path selection, displaying dropdown with suggested JDKs and expose browse button.
 */
class GradleJdkPathEditComboBox(
  private val suggestedJdkPaths: List<LabelAndFileForLocation>,
  private var currentJdkPath: @SystemIndependent String?,
  private val hintMessage: String,
) : JPanel(VerticalLayout(0)), ItemListener {

  val isModified: Boolean
    get() = selectedJdkPath != currentJdkPath
  val selectedJdkPath: String
    get() = jdkComboBox.editor.item.toString()
  @get:VisibleForTesting
  val itemCount: Int
    get() = jdkComboBox.itemCount
  @get:VisibleForTesting
  var selectedItem: Any?
    get() = jdkComboBox.selectedItem
    set(value) { jdkComboBox.selectedItem = value }
  @get:VisibleForTesting
  val editor: ComboBoxEditor
    get() = jdkComboBox.editor

  private val jdkComboBox = ComboBox<LabelAndFileForLocation>()

  init {
    initJdkComboBox()
    createJdkComboBox()
  }

  private fun initJdkComboBox() {
    jdkComboBox.apply {
      isEditable = true
      editor = createJdkComboBoxEditor()
      renderer = JdkComboBoxCellRenderer()
      addItemListener(this@GradleJdkPathEditComboBox)
      suggestedJdkPaths.forEach { jdkComboBox.addItem(it) }
      maximumRowCount = 10
    }
    resetSelection()
  }

  private fun createJdkComboBox() {
    panel {
      row {
        add(jdkComboBox)
        comment(comment = hintMessage) { BrowserUtil.browse(it.url) }.applyToComponent {
          foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
          font = JBFont.small()
        }
      }
    }.also {
      add(it)
    }
  }

  fun applySelection() {
    currentJdkPath = selectedJdkPath
  }

  fun resetSelection() {
    jdkComboBox.editor.item = currentJdkPath
  }

  @VisibleForTesting
  fun getItemAt(index: Int): LabelAndFileForLocation = jdkComboBox.getItemAt(index)

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(minimumAndPreferredWidth, preferredSize.height)
  }

  override fun itemStateChanged(event: ItemEvent?) {
    val eventSelectedItem = event?.item
    if (event?.stateChange == ItemEvent.SELECTED && eventSelectedItem is LabelAndFileForLocation) {
      jdkComboBox.selectedItem = eventSelectedItem.file
    }
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    jdkComboBox.isEnabled = enabled
  }

  private fun createJdkComboBoxEditor() = GradleJdkPathComboBoxEditor(
    jdkComboBox = jdkComboBox,
    onTextChanged = { validateSelectedJdkPath() }
  )

  private fun validateSelectedJdkPath() {
    jdkComboBox.editor.editorComponent.apply {
      foreground = if (ExternalSystemJdkUtil.isValidJdk(selectedJdkPath)) JBColor.black else JBColor.red
    }
  }

  private class GradleJdkPathComboBoxEditor(
    private val jdkComboBox: ComboBox<LabelAndFileForLocation>,
    private val onTextChanged: () -> Unit
  ) : BasicComboBoxEditor() {

    override fun createEditorComponent() = ExtendableTextField().apply {
      addExtension(createJdkBrowseButton())
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          onTextChanged()
        }
      })
      border = null
    }

    private fun createJdkBrowseButton() = ExtendableTextComponent.Extension.create(
      AllIcons.General.OpenDisk,
      AllIcons.General.OpenDiskHover,
      AndroidBundle.message("gradle.settings.jdk.browse.button.tooltip.text")
    ) {
      jdkComboBox.isPopupVisible = false
      SdkConfigurationUtil.selectSdkHome(JavaSdk.getInstance()) { jdkPath ->
        jdkComboBox.editor.item = jdkPath
      }
    }
  }

  private class JdkComboBoxCellRenderer : ColoredListCellRenderer<LabelAndFileForLocation>() {
    override fun customizeCellRenderer(
      list: JList<out LabelAndFileForLocation>,
      value: LabelAndFileForLocation,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      icon = JavaSdk.getInstance().icon
      append(value.label)
      append(" ")
      append(value.systemDependentPath, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }
}