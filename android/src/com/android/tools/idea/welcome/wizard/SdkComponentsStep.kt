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
package com.android.tools.idea.welcome.wizard

import com.android.io.CancellableFileIo
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.android.tools.idea.welcome.install.ComponentInstaller
import com.android.tools.idea.welcome.install.ComponentTreeNode
import com.android.tools.idea.welcome.install.InstallableComponent
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.google.common.collect.ImmutableList
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.layout.panel
import com.intellij.ui.table.JBTable
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.containers.isEmpty
import com.intellij.util.containers.notNullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import org.jetbrains.android.sdk.AndroidSdkData
import org.jetbrains.annotations.Contract
import java.awt.BorderLayout
import java.awt.Container
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Paths
import javax.accessibility.AccessibleContext
import javax.swing.AbstractCellEditor
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Wizard page for selecting SDK components to download.
 */
class SdkComponentsStep(
  model: FirstRunModel
) : ModelWizardStep<FirstRunModel>(model, "SDK Components Setup") {
  private val tableModel: ComponentsTableModel = ComponentsTableModel(model.componentTree)
  private var componentsTable = JBTable().apply {
    setModel(tableModel)
    tableHeader = null
    columnModel.getColumn(0).apply {
      cellRenderer = SdkComponentRenderer()
      cellEditor = SdkComponentRenderer()
    }

    selectionModel.addListSelectionListener {
      componentDescription.text = "".takeIf { selectedRow < 0 } ?: tableModel.getComponentDescription(selectedRow)
      updateDiskSizes()
    }
  }
  private val componentsSize: Long
    get() = model.componentTree.childrenToInstall.map(InstallableComponent::downloadSize).sum()

  private val instructionsLabel = JBLabel("Check the components you want to update/install. Click Next to continue.")
  private val contentPanel = JBLoadingPanel(BorderLayout(), this).apply {
    contentPanel!!.add(componentsTable, BorderLayout.CENTER)
  }

  private var componentDescription = JTextPane().apply {
    isEditable = false
  }

  private var body = Splitter(false, 0.5f, 0.2f, 0.8f).apply {
    isShowDividerIcon = false
    isShowDividerControls = false
    firstComponent = ScrollPaneFactory.createScrollPane(contentPanel, false)
    secondComponent = ScrollPaneFactory.createScrollPane(componentDescription, false)
  }


  private val sdkLocationLabel = JBLabel("Android SDK Location:")

  private val neededSpace = JBLabel("Loading...")
  private val availableSpace = JBLabel("Loading...")
  private val innerPanel = panel {
    row("Total download size: ") {
      neededSpace()
    }
    row("Available space: ") {
      availableSpace()
    }
  }

  private val sdkPath = TextFieldWithBrowseButton().apply {
    text = model.sdkLocation.toString()
  }
  private val errorMessage = JBLabel("Label")

  private val outerPanel = panel {
    row {
      instructionsLabel()
    }
    row {
      body(grow, push)
    }
    row {
      innerPanel()
    }
    row {
      sdkLocationLabel()
    }
    row {
      sdkPath()
    }
  }

  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, outerPanel)
  private val root = wrapWithVScroll(validatorPanel)

  private fun updateDiskSizes() {
    availableSpace.text = getDiskSpace(sdkPath.text)
    neededSpace.text = getSizeLabel(componentsSize)
  }

  init {
    // Since we create and initialize a new AndroidSdkHandler/RepoManager for every (partial)
    // path that's entered, disallow direct editing of the path.
    sdkPath.isEditable = false

    if (!IdeInfo.getInstance().isGameTools) {
      // Game tools does not allow changing Android SDK install directory from the UI.
      sdkPath.addBrowseFolderListener(
        "Android SDK", "Select Android SDK install directory", null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }
    val smallLabelFont = JBUI.Fonts.smallFont()
    neededSpace.font = smallLabelFont
    availableSpace.font = smallLabelFont
    errorMessage.text = null

    validatorPanel.apply {
      registerValidator(TextProperty(sdkPath), SdkPathValidator())
    }

    updateDiskSizes()
  }

  override fun getComponent(): JComponent = root

  inner class SdkPathValidator : Validator<String> {
    override fun validate(value: String): Validator.Result {
      val defaultValidatorResult = PathValidator.forAndroidSdkLocation().validate(Paths.get(value))

      if (defaultValidatorResult.severity == Validator.Severity.ERROR) {
        return defaultValidatorResult
      }

      val path = sdkPath.text
      val filesystem = getTargetFilesystem(path)

      if (!(filesystem == null || filesystem.freeSpace > componentsSize)) {
        return Validator.Result(Validator.Severity.ERROR, "Target drive does not have enough free space.")
      }

      if (defaultValidatorResult.severity == Validator.Severity.WARNING) {
        return defaultValidatorResult
      }

      return when {
        isNonEmptyNonSdk(path) -> {
          Validator.Result(Validator.Severity.WARNING, "Target folder is neither empty nor does it point to an existing SDK installation.")
        }
        isExistingSdk(path) -> {
          Validator.Result(
            Validator.Severity.WARNING,
            "An existing Android SDK was detected. The setup wizard will only download missing or outdated SDK components."
          )
        }
        else -> Validator.Result.OK
      }
    }
  }


  override fun getPreferredFocusComponent(): JComponent = componentsTable

  // This belonged to InstallComponentPath before. TODO: maybe it should actually be in onWizardStarting to avoid/reduce freezes?
  lateinit var componentInstaller: ComponentInstaller

  init {
    val localHandler = model.localHandler
    //if (!model.sdkExists) {
    //  break // TODO(qumeric): is it correct?
    //}
    val sdkLocation = model.sdkLocation

    if (!FileUtil.filesEqual(localHandler.location?.toFile(), sdkLocation)) {
      val progress = StudioLoggerProgressIndicator(javaClass)
      contentPanel.startLoading()
      localHandler.getSdkManager(progress)
        .load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(
          RepoLoadedListener {
            componentInstaller = ComponentInstaller(localHandler)
            model.componentTree.updateState(localHandler)
            contentPanel.stopLoading()
          }), ImmutableList.of(Runnable { loadingError() }),
              StudioProgressRunner(false, false, "Finding Available SDK Components", null), StudioDownloader(),
              StudioSettingsController.getInstance())
    }
  }

  private fun loadingError() {
    componentDescription.text = "There was an error while loading a list of components. Please try to restart Android Studio."
  }

  private inner class SdkComponentRenderer : AbstractCellEditor(), TableCellRenderer, TableCellEditor {
    private val panel = RendererPanel()
    private val checkBox = RendererCheckBox().apply {
      isOpaque = false
      addActionListener {
        if (componentsTable.isEditing) {
          // Stop cell editing as soon as the SPACE key is pressed. This allows the SPACE key to toggle the checkbox while allowing
          // the other navigation keys to function as soon as the toggle action is finished.
          // Note: This calls "setValueAt" on "myTableModel" automatically.
          stopCellEditing()
        }
        else {
          // This happens when the "pressed" action is invoked programmatically through accessibility,
          // so we need to call "setValueAt" manually.
          tableModel.setValueAt(isSelected, row, 0)
        }
        updateDiskSizes()
      }
    }
    private var emptyBorder: Border? = null

    override fun getTableCellRendererComponent(
      table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): RendererPanel = panel.also {
      setupControl(table, value, row, isSelected, hasFocus)
    }

    private fun setupControl(table: JTable, value: Any, row: Int, isSelected: Boolean, hasFocus: Boolean) {
      val background = table.selectionBackground.takeIf { isSelected } ?: table.background
      val foreground = table.selectionForeground.takeIf { isSelected } ?: table.foreground
      panel.background = background
      panel.border = getCellBorder(table, isSelected && hasFocus)
      checkBox.row = row
      checkBox.foreground = foreground
      panel.remove(checkBox)

      val pair = value as Pair<ComponentTreeNode, Int>?
      var indent = 0
      if (pair != null) {
        val node = pair.getFirst()
        checkBox.isEnabled = node.isEnabled
        checkBox.text = node.label
        checkBox.isSelected = node.isChecked
        indent = pair.getSecond()
      }
      panel.add(checkBox,
                GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, indent * 2))
    }

    private fun getCellBorder(table: JTable, isSelectedFocus: Boolean): Border {
      val focusedBorder = UIUtil.getTableFocusCellHighlightBorder()
      return if (isSelectedFocus) {
        focusedBorder
      }
      else {
        emptyBorder = emptyBorder ?: EmptyBorder(focusedBorder.getBorderInsets(table))
        emptyBorder!!
      }
    }

    override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int) = panel.also {
      setupControl(table, value, row, true, true)
    }

    override fun getCellEditorValue(): Any = checkBox.isSelected

    /**
     * A specialization of [JPanel] that provides complete accessibility support by
     * delegating most of its behavior to [checkBox].
     */
    private inner class RendererPanel : JPanel(GridLayoutManager(1, 1)) {
      override fun processKeyEvent(e: KeyEvent) {
        if (componentsTable.isEditing) {
          checkBox._processKeyEvent(e)
        }
        else {
          super.processKeyEvent(e)
        }
      }

      override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
        return if (componentsTable.isEditing) {
          checkBox._processKeyBinding(ks, e, condition, pressed)
        }
        else {
          super.processKeyBinding(ks, e, condition, pressed)
        }
      }

      override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
          accessibleContext = AccessibleRendererPanel()
        }
        return accessibleContext
      }

      /**
       * Delegate accessible implementation to the embedded [checkBox].
       */
      private inner class AccessibleRendererPanel : AccessibleContextDelegate(checkBox.accessibleContext) {
        override fun getDelegateParent(): Container = this@RendererPanel.parent

        override fun getAccessibleDescription(): String = tableModel.getComponentDescription(checkBox.row)
      }
    }

    /**
     * A specialization of [JCheckBox] that provides keyboard friendly behavior
     * when contained inside [RendererPanel] inside a table cell editor.
     */
    private inner class RendererCheckBox : JCheckBox() {
      var row: Int = 0

      fun _processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean =
        super.processKeyBinding(ks, e, condition, pressed)

      fun _processKeyEvent(e: KeyEvent) = super.processKeyEvent(e)

      override fun requestFocus() {
        // Ignore focus requests when editing cells. If we were to accept the focus request
        // the focus manager would move the focus to some other component when the checkbox
        // exits editing mode.
        if (componentsTable.isEditing) {
          return
        }

        super.requestFocus()
      }
    }
  }

  // TODO(qumeric): make private
  class ComponentsTableModel(component: ComponentTreeNode) : AbstractTableModel() {
    private val components: List<Pair<ComponentTreeNode, Int>>

    init {
      val components = ImmutableList.builder<Pair<ComponentTreeNode, Int>>()
      // Note that root component is not present in the table model so the tree appears to have multiple roots
      traverse(component.immediateChildren, 0, components)
      this.components = components.build()
    }

    private fun traverse(
      children: Collection<ComponentTreeNode>, indent: Int, components: ImmutableList.Builder<Pair<ComponentTreeNode, Int>>
    ) {
      for (child in children) {
        components.add(Pair.create(child, indent))
        traverse(child.immediateChildren, indent + 1, components)
      }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0 && getInstallableComponent(rowIndex).isEnabled

    override fun getRowCount(): Int = components.size

    override fun getColumnCount(): Int = 1

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = components[rowIndex]

    private fun getInstallableComponent(rowIndex: Int): ComponentTreeNode = components[rowIndex].getFirst()

    override fun setValueAt(aValue: Any?, row: Int, column: Int) {
      val node = getInstallableComponent(row)
      node.toggle(aValue as Boolean)
      // We need to repaint as a change in a single row may affect the state of
      // our parent and/or children in other rows.
      // Note: Don't use fireTableDataChanged to avoid clearing the selection.
      fireTableRowsUpdated(0, rowCount)
    }

    fun getComponentDescription(index: Int): String = getInstallableComponent(index).description
  }
}


// TODO(qumeric): make private
@Contract("null->null")
fun getExistingParentFile(path: String?): File? {
  if (path.isNullOrEmpty()) {
    return null
  }

  return generateSequence(File(path).absoluteFile) { it.parentFile }.firstOrNull(File::exists)
}

// TODO(qumeric): make private
fun getDiskSpace(path: String?): String {
  val file = getTargetFilesystem(path) ?: return ""
  val available = getSizeLabel(file.freeSpace)
  return if (SystemInfo.isWindows) {
    val driveName = generateSequence(file, File::getParentFile).last().name
    "$available (drive $driveName)"
  }
  else {
    available
  }
}

// TODO(qumeric): make private
fun getTargetFilesystem(path: String?): File? = getExistingParentFile(path) ?: File.listRoots().firstOrNull()

@Contract("null->false")
// TODO(qumeric): make private
fun isExistingSdk(path: String?): Boolean {
  if (path.isNullOrBlank()) {
    return false
  }
  return File(path).run { isDirectory && IdeSdks.getInstance().isValidAndroidSdkPath(this) }
}

@Contract("null->false")
// TODO(qumeric): make private
fun isNonEmptyNonSdk(path: String?): Boolean {
  if (path == null) {
    return false
  }
  val file = File(path)
  return file.exists() && !CancellableFileIo.list(file.toPath()).notNullize().isEmpty() && AndroidSdkData.getSdkData(file) == null
}
