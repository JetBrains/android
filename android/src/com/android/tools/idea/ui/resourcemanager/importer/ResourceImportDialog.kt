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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.tools.adtui.common.ProposedFileTreeCellRenderer
import com.android.tools.adtui.common.ProposedFileTreeModel
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.ui.resourcemanager.widget.DetailedPreview
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.ide.wizard.Step
import com.intellij.ide.wizard.StepAdapter
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBCardLayout
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.ItemEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.BorderFactory
import javax.swing.GroupLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val DIALOG_TITLE = "Import drawables"
private val DIALOG_SIZE = JBUI.size(1000, 700)

private val ASSET_GROUP_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
  JBUI.Borders.empty(12, 0, 8, 0))

private val NORTH_PANEL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
  JBUI.Borders.empty(16))

private val CONTENT_PANEL_BORDER = JBUI.Borders.empty(0, 20)

/**
 * Dialog allowing user to edit option before importing resources.
 */
class ResourceImportDialog(
  private val dialogViewModel: ResourceImportDialogViewModel
) : AbstractWizard<Step>(DIALOG_TITLE, dialogViewModel.facet.module.project) {

  constructor(facet: AndroidFacet, assetSets: Sequence<DesignAsset>) :
    this(ResourceImportDialogViewModel(facet, assetSets))

  private val assetSetToView = IdentityHashMap<ResourceAssetSet, DesignAssetSetView>()

  private val content = JPanel(VerticalLayout(0)).apply {
    border = CONTENT_PANEL_BORDER
  }

  val root = JBScrollPane(content).apply {
    preferredSize = DIALOG_SIZE
    border = null
  }

  private val fileCountLabel = JBLabel()

  private val northPanel = JPanel(BorderLayout()).apply {
    border = NORTH_PANEL_BORDER
    add(fileCountLabel, BorderLayout.WEST)
    add(createImportButtonAction(), BorderLayout.EAST)
  }

  private val focusPropertyChangeListener = PropertyChangeListener { evt ->
    if (evt.newValue !is JComponent) {
      return@PropertyChangeListener
    }
    val focused: JComponent = evt.newValue as JComponent
    scrollViewPortIfNeeded(focused)
  }

  init {
    addWizardSteps()
    setSize(DIALOG_SIZE.width(), DIALOG_SIZE.height())
    setResizable(false)
    dialogViewModel.updateCallback = ::updateValues
    init()
    dialogViewModel.assetSets.forEach(this::addDesignAssetSet)
    updateValues()
    setupWindowListener()
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusPropertyChangeListener)
  }

  /**
   * Setup a window listener that will display the file picker as soon as the dialog
   * opens if the [ResourceImportDialogViewModel] contains no asset.
   */
  private fun setupWindowListener() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    window.addWindowListener(object : WindowAdapter() {
      override fun windowActivated(e: WindowEvent?) {
        dialogViewModel.importMoreAssetIfEmpty(this@ResourceImportDialog::addAssets)
        // Remove the listener after the first call, otherwise it will be displayed
        // each time the dialog has the focus.
        e?.window?.removeWindowListener(this)
      }
    })
  }

  private fun addWizardSteps() {
    addStep(object : StepAdapter() {
      override fun _commit(finishChosen: Boolean) {
        if (doValidateAll().isEmpty()) {
          dialogViewModel.commit()
        }
      }
      override fun getComponent() = root
    })
    addStep(SummaryStep(dialogViewModel.summaryScreenViewModel))
  }

  override fun createNorthPanel() = northPanel

  private fun updateValues() {
    val importedAssetCount = dialogViewModel.fileCount
    fileCountLabel.text = "$importedAssetCount ${StringUtil.pluralize("resource", importedAssetCount)} ready to be imported"
  }

  private fun addDesignAssetSet(assetSet: ResourceAssetSet) {
    val view = DesignAssetSetView(assetSet)
    content.add(view)
    assetSetToView[assetSet] = view
  }

  /**
   * If a [DesignAssetSetView] already exists for [designAssetSet], merge the [newDesignAssets]
   * within this view, otherwise create a new [DesignAssetSetView].
   */
  private fun addAssets(designAssetSet: ResourceAssetSet,
                        newDesignAssets: List<DesignAsset>) {
    val existingView = assetSetToView[designAssetSet]
    if (existingView != null) {
      newDesignAssets.forEach(existingView::addAssetView)
    }
    else {
      addDesignAssetSet(designAssetSet)
    }
    updateStep()
  }

  private fun createImportButtonAction(): JComponent {
    val importAction = object : DumbAwareAction("Import more assets", "Import more assets", AllIcons.Actions.Upload) {
      override fun actionPerformed(e: AnActionEvent) {
        dialogViewModel.importMoreAssets { designAssetSet, newDesignAssets ->
          if (newDesignAssets.isNotEmpty()) {
            jumpToImportStep()
          }
          addAssets(designAssetSet, newDesignAssets)
        }
      }
    }

    val presentation = importAction.templatePresentation.clone()
    presentation.text = "Import more files"
    return ActionButtonWithText(importAction, presentation, "Resource Explorer", JBUI.size(25)).apply { isFocusable = true }
  }

  /**
   * Jump back to the import step and scroll the the end so the last added files are
   * visible.
   */
  private fun jumpToImportStep() {
    myCurrentStep = 0
    updateStep(JBCardLayout.SwipeDirection.BACKWARD)
    root.viewport.scrollRectToVisible(Rectangle(content.width, content.height, content.width + 1, content.height + 1))
  }

  /**
   * View showing a [ResourceAssetSet] and its contained [DesignAsset].
   */
  private inner class DesignAssetSetView(private var assetSet: ResourceAssetSet) : JPanel(BorderLayout(0, 0)) {
    val assetNameLabel = JBTextField(assetSet.name, 20).apply {
      this.font = StartupUiUtil.labelFont.deriveFont(JBUI.scaleFontSize(14f))
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          performRename(e.document.getText(0, document.length))
          ComponentValidator.getInstance(this@apply).ifPresent(ComponentValidator::revalidate)
        }
      })
      installValidator()
    }

    private fun JTextField.installValidator() {
      ComponentValidator(disposable).withValidator(Supplier {
        dialogViewModel.validateName(this.text, this).also {
          updateButtons()
        }
      })
        .installOn(this)
        .revalidate()
    }

    val itemNumberLabel = JBLabel(dialogViewModel.getItemNumberString(assetSet),
                                  UIUtil.ComponentStyle.SMALL,
                                  UIUtil.FontColor.BRIGHTER)

    val fileViewContainer = JPanel(VerticalFlowLayout(true, false)).apply {
      assetSet.designAssets.forEach { asset ->
        add(singleAssetView(asset))
      }
    }

    private val header = JPanel(BorderLayout()).apply {
      border = ASSET_GROUP_BORDER
      add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
        (layout as FlowLayout).alignOnBaseline = true
        add(assetNameLabel)
        add(itemNumberLabel)
      }, BorderLayout.WEST)
    }

    init {
      add(header, BorderLayout.NORTH)
      add(fileViewContainer)
    }

    fun addAssetView(asset: DesignAsset) {
      fileViewContainer.add(singleAssetView(asset))
    }

    private fun singleAssetView(asset: DesignAsset): FileImportRow {
      val viewModel = dialogViewModel.createFileViewModel(asset, removeCallback = this::removeAsset)
      val fileImportRow = FileImportRow(viewModel)
      dialogViewModel.getAssetPreview(asset).whenComplete { image, _ ->
        image?.let {
          fileImportRow.preview.icon = ImageIcon(it)
          fileImportRow.preview.repaint()
        }
      }
      return fileImportRow
    }

    private fun performRename(assetName: String) {
      dialogViewModel.rename(assetSet, assetName) { renamedAssetSet ->
        val assetSetView = assetSetToView.remove(assetSet)!!
        assetSet = renamedAssetSet
        assetSetToView[renamedAssetSet] = assetSetView
      }
    }

    private fun removeAsset(it: DesignAsset) {
      dialogViewModel.removeAsset(it)
      itemNumberLabel.text = dialogViewModel.getItemNumberString(assetSet)
      if (fileViewContainer.componentCount == 0) {
        assetSetToView.remove(this.assetSet, this)
        parent.remove(this)
        root.revalidate()
        root.repaint()
        updateStep()
      }
    }
  }

  /**
   * Scroll the [JBScrollPane] to the location of the [component] if it is not
   * within the visible area.
   */
  private fun scrollViewPortIfNeeded(component: JComponent) {
    if (content.isAncestorOf(component)) {
      val viewPortLocationOnScreen = root.viewport.locationOnScreen
      val focusedBounds = component.locationOnScreen
      val viewportWidth = root.viewport.width
      val viewportHeight = root.viewport.height
      val visibleInViewport = (focusedBounds.y < viewPortLocationOnScreen.y
                               || focusedBounds.y > viewPortLocationOnScreen.y + viewportHeight)
      if (visibleInViewport) {
        focusedBounds.translate(-viewPortLocationOnScreen.x, -viewPortLocationOnScreen.y)
        component.scrollRectToVisible(Rectangle(0, 0,
                                                viewportWidth,
                                                viewportHeight))
      }
    }
  }

  override fun canGoNext(): Boolean {
    return dialogViewModel.assetSets.isNotEmpty() && doValidateAll().isEmpty()
  }

  override fun updateButtons(lastStep: Boolean, canGoNext: Boolean, firstStep: Boolean) {
    super.updateButtons(lastStep, canGoNext, firstStep)
    if (lastStep) {
      nextButton.text = "Import"
    }
  }

  override fun getHelpID(): String {
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/write/resource-manager"
  }

  override fun doValidate(): ValidationInfo? {
    val result = dialogViewModel.getValidationInfo()
    setErrorInfoAll(if (result != null) listOf(result) else emptyList())
    return result
  }

  override fun getValidationThreadToUse(): Alarm.ThreadToUse = Alarm.ThreadToUse.POOLED_THREAD

  override fun dispose() {
    super.dispose()
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusPropertyChangeListener)
  }
}

/**
 * UI of the second step of the resource wizard showing a summary of the file being imported.
 */
private class SummaryStep(private val viewModel: SummaryScreenViewModel) : StepAdapter() {

  private var root: JComponent? = null

  private val preview = DetailedPreview().apply {
    border = JBUI.Borders.emptyTop(6)
  }

  private val fileTree = createTree()

  private var pendingFuture: CompletableFuture<*>? = null

  init {
    viewModel.updateCallback = this::update
  }

  override fun _init() {
    root = JBSplitter(false, 0.5f).apply {
      isShowDividerControls = true
      isShowDividerIcon = true
      firstComponent = createFileTreePreview()
      secondComponent = preview
      dividerWidth = JBUI.scale(4)
    }
  }

  private fun createFileTreePreview() = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.merge(border, JBUI.Borders.empty(8), true)
    add(createSourceSetSelectionCombo(), BorderLayout.NORTH)
    add(JBScrollPane(fileTree).apply {
      background = UIUtil.getPanelBackground()
    })
  }

  /**
   * Panel showing a dropdown to select the target source set
   * and a tree of the file to be imported
   */
  private fun createSourceSetSelectionCombo() = JPanel().apply {
    val groupLayout = GroupLayout(this)
    layout = groupLayout
    val label = JBLabel("Source Set:")
    val comboBox = ComboBox<SourceSetResDir>(viewModel.availableResDirs).apply {
      addItemListener { itemEvent ->
        if (itemEvent.stateChange == ItemEvent.SELECTED) {
          viewModel.selectedResDir = itemEvent.item as SourceSetResDir
        }
      }
      renderer = object : ColoredListCellRenderer<SourceSetResDir>() {
        override fun customizeCellRenderer(list: JList<out SourceSetResDir>,
                                           value: SourceSetResDir,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
          append(value.sourceSetName)
          append(" ")
          append(viewModel.getUserFormattedPath(value.absolutePath), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
        }
      }
    }
    groupLayout.setHorizontalGroup(groupLayout.createSequentialGroup()
                                     .addComponent(label)
                                     .addComponent(comboBox, 50, comboBox.preferredSize.width, Int.MAX_VALUE))

    groupLayout.setVerticalGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
                                   .addComponent(label)
                                   .addComponent(comboBox))
  }

  override fun _commit(finishChosen: Boolean) {
    if (finishChosen) {
      viewModel.doImport()
    }
  }

  override fun getComponent(): JComponent? = root

  private fun update() {
    if (viewModel.fileTreeModel !== fileTree.model) {
      val previousSelectionRow = fileTree.leadSelectionRow
      fileTree.model = viewModel.fileTreeModel
      TreeUtil.expandAll(fileTree)
      if (previousSelectionRow >= 0) {
        fileTree.setSelectionRow(previousSelectionRow)
      }
      else {
        TreeUtil.selectPath(fileTree, TreeUtil.getFirstLeafNodePath(fileTree))
      }
    }
    preview.data = viewModel.metadata
    pendingFuture?.cancel(true)
    pendingFuture = viewModel.getPreview().whenComplete { icon, _ ->
      pendingFuture = null
      preview.icon = icon
    }
  }

  private fun createTree() = Tree(viewModel.fileTreeModel).apply {
    cellRenderer = ProposedFileTreeCellRenderer()
    background = UIUtil.getTreeBackground()
    addTreeSelectionListener { selectionEvent ->
      val node = selectionEvent.newLeadSelectionPath?.lastPathComponent as? ProposedFileTreeModel.Node
      viewModel.selectedFile = if (node != null && node.isLeaf()) node.file else null
    }
    TreeUtil.expandAll(this)
  }
}