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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.adtui.common.WrappedFlowLayout
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.ui.resourcemanager.model.designAssets
import com.android.tools.idea.ui.resourcemanager.rendering.AssetIcon
import com.android.tools.idea.ui.resourcemanager.rendering.DefaultIconProvider
import com.android.tools.idea.ui.resourcemanager.widget.AssetView
import com.android.tools.idea.ui.resourcemanager.widget.Separator
import com.android.tools.idea.ui.resourcemanager.widget.SingleAssetCard
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
import com.intellij.ui.components.JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.WeakHashMap
import javax.swing.AbstractAction
import javax.swing.ActionMap
import javax.swing.BorderFactory
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants

private val ASSET_CARD_WIDTH get() = JBUI.scale(150)

private val SEPARATOR_BORDER get() = JBUI.Borders.empty(2, 4)

private val HEADER_PANEL_BORDER get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
  JBUI.Borders.empty(6, 5))

private val BACK_BUTTON_SIZE get() = JBUI.size(20)

/**
 * A [JPanel] displaying the [DesignAsset]s composing the provided [designAssetSet].
 * When double clicking on the the [DesignAsset], it opens the corresponding file.
 *
 * @param viewModel an existing instance of [ResourceExplorerListViewModel]
 * @param backCallback a callback that will be called to remove this view and show the previous one.
 *                     The callback receives this view as a parameter to allow the parent view to remove it.
 */
class ResourceDetailView(
  private val designAssetSet: ResourceAssetSet,
  private val viewModel: ResourceExplorerListViewModel,
  private val backCallback: (ResourceDetailView) -> Unit)
  : JPanel(BorderLayout()), DataProvider {

  private val viewToAsset = WeakHashMap<AssetView, DesignAsset>(designAssetSet.assets.size)
  private var lastFocusedAsset: AssetView? = null

  private val backAction = object : AnAction(AllIcons.Actions.Back) {
    init {
      ResourceManagerTracking.logDetailViewOpened(viewModel.facet, designAssetSet.assets.firstOrNull()?.type)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = true
    }
    override fun actionPerformed(e: AnActionEvent) = navigateBack()
    override fun isDumbAware(): Boolean = true
  }

  private val sharedInputMap = InputMap().apply { put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), backAction) }

  private val sharedActionMap = ActionMap().apply {
    put(backAction, object : AbstractAction("VK_ESCAPE") {
      override fun actionPerformed(e: ActionEvent?) {
        navigateBack()
      }
    })
  }

  private val cardPopupHandler = object : PopupHandler() {
    val actionManager = ActionManager.getInstance()
    val actionGroup = actionManager.getAction("ResourceExplorer") as ActionGroup

    override fun invokePopup(comp: Component?, x: Int, y: Int) {
      (comp as? AssetView)?.let { card ->
        val lastCard = lastFocusedAsset
        // Set the last focused card to get the right ContextData, but revert the assignation to let the FocusListener handle the UI
        lastFocusedAsset = card
        actionManager.createActionPopupMenu("ResourceExplorer", actionGroup).also { popUpMenu ->
          popUpMenu.setTargetComponent(card)
          popUpMenu.component.show(card, x, y)
        }
        lastFocusedAsset = lastCard
      }
    }
  }

  private val cardFocusListener = object : FocusListener {
    override fun focusLost(e: FocusEvent?) {
    }

    override fun focusGained(e: FocusEvent?) {
      (e?.source as? SingleAssetCard)?.let { assetCard ->
        if (lastFocusedAsset != assetCard) {
          lastFocusedAsset?.selected = false
          lastFocusedAsset?.focused = false
          lastFocusedAsset = assetCard
        }
        assetCard.selected = true
        assetCard.focused = true
      }
    }
  }

  /**
   * The header component showing a button to navigate back and the name of the [designAssetSet]
   */
  private val header = JPanel(HorizontalLayout(0, SwingConstants.CENTER)).apply {
    border = HEADER_PANEL_BORDER
    add(HorizontalLayout.LEFT, ActionButton(backAction, backAction.templatePresentation.clone(), "Resource Explorer",
                                            BACK_BUTTON_SIZE))
    add(HorizontalLayout.LEFT, Separator(SEPARATOR_BORDER))
    add(HorizontalLayout.LEFT, JBLabel(designAssetSet.name))
  }
  /**
   * The panel which displays all the [DesignAsset]
   */
  private val content = JPanel(WrappedFlowLayout(FlowLayout.LEFT)).apply {
    designAssetSet.designAssets.forEach { asset ->
      val assetCard = createAssetCard(asset)
      add(assetCard)
      viewToAsset[assetCard] = asset
    }
    registerBackOnEscape()
    registerFocusOnClick()
    getComponent(0).requestFocusInWindow()
  }

  init {
    add(header, BorderLayout.NORTH)
    add(JBScrollPane(content, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = null
    })
  }

  /**
   * Register focus request on mouse click and invocation of [backCallback] when
   * the ESC key is pressed.
   */
  private fun JComponent.registerBackOnEscape() {
    setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, sharedInputMap)
    actionMap = sharedActionMap
  }

  private val focusRequestMouseAdapter = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      (e.source as JComponent).requestFocusInWindow()
    }
  }

  /**
   * Register a [MouseAdapter] to request the focus in [content] on click.
   */
  private fun JComponent.registerFocusOnClick() {
    addMouseListener(focusRequestMouseAdapter)
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
    viewWidth = ASSET_CARD_WIDTH
    viewModel.assetPreviewManager.getDataProvider(asset.type).getAssetData(asset).let { assetData ->
      title = assetData.title
      subtitle = assetData.subtitle
      metadata = assetData.metadata
    }
    val previewManager = viewModel.assetPreviewManager
    val previewProvider = previewManager.getPreviewProvider(asset.type)
    withChessboard = previewProvider.supportsTransparency
    val assetIcon = AssetIcon(previewManager, asset, thumbnailSize.width,
                              thumbnailSize.height)
    val iconLabel = JBLabel(assetIcon).apply { verticalAlignment = SwingConstants.CENTER }
    // DefaultIconProvider provides an empty icon, to avoid comparison, we just set the thumbnail to null.
    thumbnail = if (previewProvider is DefaultIconProvider) null else iconLabel
    addFocusListener(cardFocusListener)
    addMouseListener(cardPopupHandler)
    // Mouse listener to open the file on double click
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        requestFocusInWindow()
        if (e.clickCount == 2) {
          openFile(asset)
        }
        e.consume()
      }
    })
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (KeyEvent.VK_ENTER == e.keyCode) {
          openFile(asset)
        }
      }
    })

    registerBackOnEscape()
    isFocusable = true
    isRequestFocusEnabled = true
  }

  private fun openFile(asset: DesignAsset) {
    ResourceManagerTracking.logAssetOpened(viewModel.facet, asset.type)
    viewModel.doSelectAssetAction(asset)
  }

  override fun getData(dataId: String): Any? {
    val assetList = viewToAsset[lastFocusedAsset]?.let { listOf(it) } ?: return null
    return viewModel.getData(dataId, assetList)
  }

  override fun requestFocusInWindow(): Boolean {
    return content.getComponent(0).requestFocusInWindow()
  }
}

