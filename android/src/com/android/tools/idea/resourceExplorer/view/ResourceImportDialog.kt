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

import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.FileImportRowViewModel
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceImportDialogViewModel
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel

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
) : DialogWrapper(dialogViewModel.facet.module.project, true, DialogWrapper.IdeModalityType.MODELESS) {

  constructor(facet: AndroidFacet, assetSets: Sequence<DesignAsset>) :
    this(ResourceImportDialogViewModel(facet, assetSets))

  private val assetSetToView = mutableMapOf<String, DesignAssetSetView>()

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
    setOKButtonText("Import")
    setSize(DIALOG_SIZE.width(), DIALOG_SIZE.height())
    setResizable(false)
    isOKActionEnabled = true
    title = DIALOG_TITLE
    dialogViewModel.updateCallback = ::updateValues
    init()
    dialogViewModel.assetSets.forEach(this::addDesignAssetSet)
    updateValues()
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusPropertyChangeListener)
  }

  override fun doOKAction() {
    dialogViewModel.doImport()
    super.doOKAction()
  }

  override fun createCenterPanel() = root
  override fun createNorthPanel() = northPanel
  override fun getStyle() = DialogStyle.COMPACT

  private fun updateValues() {
    val importedAssetCount = dialogViewModel.fileCount
    fileCountLabel.text = "$importedAssetCount ${StringUtil.pluralize("resource", importedAssetCount)} ready to be imported"
  }

  private fun addDesignAssetSet(assetSet: DesignAssetSet) {
    val view = DesignAssetSetView(assetSet)
    content.add(view)
    assetSetToView[assetSet.name] = view
  }

  /**
   * If a [DesignAssetSetView] already exists for [designAssetSet], merge the [newDesignAssets]
   * within this view, otherwise create a new [DesignAssetSetView].
   */
  private fun addAssets(designAssetSet: DesignAssetSet,
                        newDesignAssets: List<DesignAsset>) {
    val existingView = assetSetToView[designAssetSet.name]
    if (existingView != null) {
      newDesignAssets.forEach(existingView::addAssetView)
    }
    else {
      addDesignAssetSet(designAssetSet)
    }
  }

  private fun createImportButtonAction(): JComponent {
    val importAction = object : DumbAwareAction("Import more assets", "Import more assets", AllIcons.Actions.Upload) {
      override fun actionPerformed(e: AnActionEvent) {
        dialogViewModel.importMoreAssets { designAssetSet, newDesignAssets ->
          addAssets(designAssetSet, newDesignAssets)
        }
      }
    }

    val presentation = importAction.templatePresentation.clone()
    presentation.text = "Import more files"
    return ActionButtonWithText(importAction, presentation, "Resource Explorer", JBUI.size(25)).apply { isFocusable = true }
  }

  /**
   * View showing a [DesignAssetSet] and its contained [DesignAsset].
   */
  private inner class DesignAssetSetView(private val assetSet: DesignAssetSet) : JPanel(BorderLayout(0, 0)) {
    val assetNameLabel = JBLabel(assetSet.name, UIUtil.ComponentStyle.LARGE)
    val itemNumberLabel = JBLabel(dialogViewModel.getItemNumberString(assetSet),
                                  UIUtil.ComponentStyle.SMALL,
                                  UIUtil.FontColor.BRIGHTER)
    //val newAlternativeButton = JBLabel("New alternative", StudioIcons.Common.ADD, JBLabel.RIGHT)

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
      //add(newAlternativeButton, BorderLayout.EAST)
    }

    init {
      add(header, BorderLayout.NORTH)
      add(fileViewContainer)
    }

    fun addAssetView(asset: DesignAsset) {
      fileViewContainer.add(singleAssetView(asset))
    }

    private fun singleAssetView(asset: DesignAsset): FileImportRow {
      val viewModel = FileImportRowViewModel(asset, ResourceFolderType.DRAWABLE, removeCallback = this::removeAsset)
      val fileImportRow = FileImportRow(viewModel)
      dialogViewModel.getAssetPreview(asset).whenComplete { image, _ ->
        image?.let {
          fileImportRow.preview.icon = ImageIcon(it)
          fileImportRow.preview.repaint()
        }
      }
      return fileImportRow
    }

    private fun removeAsset(it: DesignAsset) {
      dialogViewModel.removeAsset(it)
      itemNumberLabel.text = dialogViewModel.getItemNumberString(assetSet)
      if (fileViewContainer.componentCount == 0) {
        assetSetToView.remove(this.assetSet.name, this)
        parent.remove(this)
        root.revalidate()
        root.repaint()
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

  override fun dispose() {
    super.dispose()
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusPropertyChangeListener)
  }
}