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

import com.android.tools.idea.resourceExplorer.ImageCache
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.ProjectResourcesBrowserViewModel
import com.android.tools.idea.resourceExplorer.widget.Separator
import com.android.tools.idea.resourceExplorer.widget.SingleAssetCard
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

private val ASSET_CARD_WIDTH = JBUI.scale(150)

private val SEPARATOR_BORDER = JBUI.Borders.empty(2, 4)

private val HEADER_PANEL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
  JBUI.Borders.empty(6, 5))

private val BACK_BUTTON_SIZE = JBUI.size(20)

/**
 * A [JPanel] displaying the [DesignAsset]s composing the provided [designAssetSet].
 * When double clicking on the the [DesignAsset], it opens the corresponding file.
 *
 * @param imageCache the [ImageCache] to reuse for the rendering of the drawable asset.
 * @param viewModel an existing instance of [ProjectResourcesBrowserViewModel]
 * @param backCallback a callback that will be called to remove this view and show the previous one.
 *                     The callback receives this view as a parameter to allow the parent view to remove it.
 */
class ResourceDetailView(
  private val designAssetSet: DesignAssetSet,
  private val imageCache: ImageCache,
  private val viewModel: ProjectResourcesBrowserViewModel,
  private val backCallback: (ResourceDetailView) -> Unit)
  : JPanel(BorderLayout()) {

  private val backAction = object : AnAction(StudioIcons.Common.BACK_ARROW) {
    init {
      templatePresentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) = navigateBack()
    override fun isDumbAware(): Boolean = true
  }

  /**
   * The header component showing a button to navigate back and the name of the [designAssetSet]
   */
  private val header = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
    border = HEADER_PANEL_BORDER
    add(ActionButton(backAction, backAction.templatePresentation.clone(), "Resource Explorer", BACK_BUTTON_SIZE))
    add(Separator(SEPARATOR_BORDER))
    add(JBLabel(designAssetSet.name))
  }

  /**
   * The panel which displays all the [DesignAsset]
   */
  private val content = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    designAssetSet.designAssets.forEach { asset ->
      add(createAssetCard(asset))
    }
    registerBackOnEscape()
    registerFocusOnClick()
  }

  init {
    add(header, BorderLayout.NORTH)
    add(content)
  }

  /**
   * Register focus request on mouse click and invocation of [backCallback] when
   * the ESC key is pressed.
   */
  private fun JComponent.registerBackOnEscape() {
    val condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    val inputMap = getInputMap(condition)

    val backAction = "VK_ESCAPE"
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), backAction)
    this.actionMap.put(backAction, object : AbstractAction(backAction) {
      override fun actionPerformed(e: ActionEvent?) {
        navigateBack()
      }
    })
  }

  /**
   * Register a [MouseAdapter] to request the focus in [content] on click.
   */
  private fun JComponent.registerFocusOnClick() {
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        (e.source as JComponent).requestFocusInWindow()
      }
    })
  }

  /**
   * Call the [backCallback] with this view as a parameter.
   */
  private fun navigateBack() {
    backCallback(this@ResourceDetailView)
  }

  /**
   * Create a [SingleAssetCard] representing the [DesignAsset].
   * The thumbnail is populated asynchronously.
   */
  private fun createAssetCard(asset: DesignAsset) = SingleAssetCard().apply {
    withChessboard = true
    viewWidth = ASSET_CARD_WIDTH
    title = asset.qualifiers.joinToString("-") { it.folderSegment }.takeIf { it.isNotBlank() } ?: "default"
    subtitle = asset.file.name
    metadata = viewModel.getSize(asset)

    val image = imageCache.computeAndGet(asset, EMPTY_ICON, false) {
      viewModel.getDrawablePreview(thumbnailSize, asset)
        .whenComplete { image, _ ->
          thumbnail = JBLabel(ImageIcon(image))
          repaint()
        }
    }
    thumbnail = JBLabel(ImageIcon(image))

    // Mouse listener to open the file on double click
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        if (e.clickCount == 2) {
          viewModel.openFile(asset)
        }
      }
    })
  }

  override fun requestFocusInWindow(): Boolean {
    return content.requestFocusInWindow()
  }
}

