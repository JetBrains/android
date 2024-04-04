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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionablePanel
import com.android.tools.idea.uibuilder.surface.layout.getScaledContentSize
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.android.tools.idea.uibuilder.surface.layout.margin
import com.android.tools.idea.uibuilder.surface.layout.scaledContentSize
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Distance between the bottom bound of model name and top bound of SceneView. */
@SwingCoordinate private const val TOP_BAR_BOTTOM_MARGIN = 3

/** Distance between the top bound of bottom bar and bottom bound of SceneView. */
@SwingCoordinate private const val BOTTOM_BAR_TOP_MARGIN = 3

/** Minimum allowed width for the SceneViewPeerPanel. */
@SwingCoordinate private const val SCENE_VIEW_PEER_PANEL_MIN_WIDTH = 100

/** Minimum allowed width for the model name label. */
@SwingCoordinate private const val MODEL_NAME_LABEL_MIN_WIDTH = 20

data class LayoutData(
  val scale: Double,
  val modelName: String?,
  val modelTooltip: String?,
  val x: Int,
  val y: Int,
  val scaledSize: Dimension,
) {

  // Used to avoid extra allocations in isValidFor calls
  private val cachedDimension = Dimension()

  /**
   * Returns whether this [LayoutData] is still valid (has not changed) for the given [SceneView]
   */
  fun isValidFor(sceneView: SceneView): Boolean =
    scale == sceneView.scale &&
      x == sceneView.x &&
      y == sceneView.y &&
      modelName == sceneView.scene.sceneManager.model.modelDisplayName &&
      scaledSize == sceneView.getContentSize(cachedDimension).scaleBy(sceneView.scale)

  companion object {
    fun fromSceneView(sceneView: SceneView): LayoutData =
      LayoutData(
        sceneView.scale,
        sceneView.scene.sceneManager.model.modelDisplayName,
        sceneView.scene.sceneManager.model.modelTooltip,
        sceneView.x,
        sceneView.y,
        sceneView.getContentSize(null).scaleBy(sceneView.scale),
      )
  }
}

/**
 * A Swing component associated to the given [SceneView]. There will be one of this components in
 * the [DesignSurface] per every [SceneView] available. This panel will be positioned on the
 * coordinates of the [SceneView] and can be used to paint Swing elements on top of the [SceneView].
 */
class SceneViewPeerPanel(
  val sceneView: SceneView,
  private val labelPanel: LabelPanel,
  private val sceneViewStatusIconAction: AnAction?,
  private val sceneViewToolbarActions: List<AnAction>,
  sceneViewBottomBar: JComponent?,
  sceneViewLeftBar: JComponent?,
  sceneViewRightBar: JComponent?,
  private val sceneViewErrorsPanel: JComponent?,
) : JPanel(), PositionablePanel, DataProvider {

  /**
   * Contains cached layout data that can be used by this panel to verify when it's been invalidated
   * without having to explicitly call [revalidate]
   */
  private var layoutData = LayoutData.fromSceneView(sceneView)

  private val cachedContentSize = Dimension()
  private val cachedScaledContentSize = Dimension()
  private val cachedPreferredSize = Dimension()

  override val positionableAdapter =
    object : PositionableContent {
      override val organizationGroup: OrganizationGroup?
        get() = sceneView.sceneManager.model.organizationGroup

      override val scale: Double
        get() = sceneView.scale

      override val x: Int
        get() = sceneView.x

      override val y: Int
        get() = sceneView.y

      override val isFocusedContent: Boolean
        get() = sceneView.isFocusedScene

      override fun getMargin(scale: Double): Insets {
        val contentSize = getContentSize(null).scaleBy(scale)
        val sceneViewMargin =
          sceneView.margin.also {
            // Extend top to account for the top toolbar
            it.top += sceneViewTopPanel.preferredSize.height
            it.bottom += sceneViewBottomPanel.preferredSize.height
            it.left += sceneViewLeftPanel.preferredSize.width
            it.right += sceneViewRightPanel.preferredSize.width
            if (sceneViewErrorsPanel?.isVisible == true) {
              it.bottom += sceneViewCenterPanel.preferredSize.height
            }
          }

        return if (contentSize.width < minimumSize.width) {
          // If there is no content, or the content is smaller than the minimum size,
          // pad the margins horizontally to occupy the empty space.
          val horizontalPadding = (minimumSize.width - contentSize.width).coerceAtLeast(0)

          JBUI.insets(
            sceneViewMargin.top,
            sceneViewMargin.left,
            sceneViewMargin.bottom,
            // The content is aligned on the left
            sceneViewMargin.right + horizontalPadding,
          )
        } else {
          sceneViewMargin
        }
      }

      override fun getContentSize(dimension: Dimension?): Dimension =
        if (sceneView.hasContentSize())
          sceneView.getContentSize(dimension).also { cachedContentSize.size = it }
        else if (!sceneView.isVisible || sceneView.hasRenderErrors()) {
          dimension?.apply { setSize(0, 0) } ?: Dimension(0, 0)
        } else {
          dimension?.apply { size = cachedContentSize } ?: Dimension(cachedContentSize)
        }

      /** Applies the calculated coordinates from this adapter to the backing SceneView. */
      private fun applyLayout() {
        getScaledContentSize(cachedScaledContentSize)
        val margin = this.margin // To avoid recalculating the size
        setBounds(
          x - margin.left,
          y - margin.top,
          cachedScaledContentSize.width + margin.left + margin.right,
          cachedScaledContentSize.height + margin.top + margin.bottom,
        )
        sceneView.scene.needsRebuildList()
      }

      override fun setLocation(x: Int, y: Int) {
        // The SceneView is painted right below the top toolbar panel.
        // This set the top-left corner of preview.
        sceneView.setLocation(x, y)

        // After positioning the view, we re-apply the bounds to the SceneViewPanel.
        // We do this even if x & y did not change since the size might have.
        applyLayout()
      }
    }

  fun PositionableContent.isEmptyContent() =
    scaledContentSize.let { it.height == 0 && it.width == 0 }

  private fun createToolbar(
    actions: List<AnAction>,
    toolbarCustomization: (ActionToolbar) -> Unit,
  ): JComponent? {
    if (actions.isEmpty()) {
      return null
    }
    return ActionManager.getInstance()
      .createActionToolbar("sceneView", DefaultActionGroup(actions), true)
      .apply {
        toolbarCustomization(this)
        targetComponent = this@SceneViewPeerPanel
      }
      .component
      .apply {
        isOpaque = false
        border = JBUI.Borders.empty()
      }
  }

  /**
   * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
   * aligned (the toolbar).
   */
  @VisibleForTesting
  val sceneViewTopPanel =
    JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyBottom(TOP_BAR_BOTTOM_MARGIN)
      isOpaque = false
      // Make the status icon be part of the top panel
      val sceneViewStatusIcon =
        sceneViewStatusIconAction?.let {
          createToolbar(listOf(sceneViewStatusIconAction)) {
            (it as? ActionToolbarImpl)?.setForceMinimumSize(true)
            it.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
          }
        }
      val sceneViewStatusIconSize = sceneViewStatusIcon?.minimumSize?.width ?: 0
      if (sceneViewStatusIcon != null && sceneViewStatusIconSize > 0) {
        add(sceneViewStatusIcon, BorderLayout.LINE_START)
        sceneViewStatusIcon.isVisible = true
      }
      add(labelPanel, BorderLayout.CENTER)
      val sceneViewToolbar =
        createToolbar(sceneViewToolbarActions) {
          // Do not allocate space for the "see more" chevron if not needed
          it.setReservePlaceAutoPopupIcon(false)
          it.setShowSeparatorTitles(true)
        }
      if (sceneViewToolbar != null) {
        add(sceneViewToolbar, BorderLayout.LINE_END)
        // Initialize the toolbar as invisible if ScreenReader is not active. In this case, its
        // visibility will be controlled by hovering the sceneViewTopPanel. When the screen reader
        // is active, the toolbar is always visible, so it can be focusable.
        sceneViewToolbar.isVisible = defaultToolbarVisibility || ScreenReader.isActive()
      }
      // The space of name label is sacrificed when there is no enough width to display the toolbar.
      // When it happens, the label will be trimmed and show the ellipsis at its tail.
      // User can still hover it to see the full label in the tooltips.
      val minWidth =
        sceneViewStatusIconSize +
          MODEL_NAME_LABEL_MIN_WIDTH +
          (sceneViewToolbar?.minimumSize?.width ?: 0)
      // Since sceneViewToolbar visibility can change, sceneViewTopPanel (its container) might want
      // to reduce its size when sceneViewToolbar
      // gets invisible, resulting in a visual misbehavior where the toolbar moves a little when the
      // actions appear/disappear. To fix this,
      // we should set sceneViewTopPanel preferred size to always occupy the height taken by
      // sceneViewToolbar when it exists.
      val minHeight =
        maxOf(
          minimumSize.height,
          sceneViewToolbar?.preferredSize?.height ?: 0,
          sceneViewToolbar?.minimumSize?.height ?: 0,
        )
      minimumSize = Dimension(minWidth, minHeight)
      preferredSize = sceneViewToolbar?.let { Dimension(minWidth, minHeight) }

      setUpTopPanelMouseListeners(sceneViewToolbar)
    }

  /**
   * Creates and adds the [MouseAdapter]s required to show the [sceneViewToolbar] when the mouse is
   * hovering the [sceneViewTopPanel], and hide it otherwise.
   */
  private fun JPanel.setUpTopPanelMouseListeners(sceneViewToolbar: JComponent?) {
    // MouseListener to show the sceneViewToolbar when the mouse enters the target component, and to
    // hide it when the mouse exits the bounds
    // of sceneViewTopPanel.
    val hoverTopPanelMouseListener =
      object : MouseAdapter() {

        override fun mouseEntered(e: MouseEvent?) {
          // Show the toolbar actions when mouse is hovering the top panel.
          sceneViewToolbar?.let { it.isVisible = true }
        }

        override fun mouseExited(e: MouseEvent?) {
          SwingUtilities.getWindowAncestor(this@setUpTopPanelMouseListeners)?.let {
            if (!it.isFocused) {
              // Dismiss the toolbar if the current window loses focus, e.g. when alt tabbing.
              hideToolbar()
              return@mouseExited
            }
          }

          e?.locationOnScreen?.let {
            SwingUtilities.convertPointFromScreen(it, this@setUpTopPanelMouseListeners)
            // Hide the toolbar when the mouse exits the bounds of sceneViewTopPanel or the
            // containing design surface.
            if (!containsExcludingBorder(it) || !designSurfaceContains(e.locationOnScreen)) {
              hideToolbar()
            } else {
              // We've exited to one of the toolbar actions, so we need to make sure this listener
              // is algo registered on them.
              sceneViewToolbar?.let { toolbar ->
                for (i in 0 until toolbar.componentCount) {
                  toolbar
                    .getComponent(i)
                    .removeMouseListener(this) // Prevent duplicate listeners being added.
                  toolbar.getComponent(i).addMouseListener(this)
                }
              }
            }
          } ?: hideToolbar()
        }

        private fun JPanel.designSurfaceContains(p: Point): Boolean {
          var component = parent
          var designSurface: DesignSurfaceScrollPane? = null
          while (component != null) {
            if (component is DesignSurfaceScrollPane) {
              designSurface = component
              break
            }
            component = component.parent
          }
          if (designSurface == null) return false
          SwingUtilities.convertPointFromScreen(p, designSurface)
          // Consider the scrollbar width exiting from the right
          return p.x in 0 until (designSurface.width - UIUtil.getScrollBarWidth()) &&
            p.y in 0 until designSurface.height
        }

        private fun JPanel.containsExcludingBorder(p: Point): Boolean {
          val borderInsets = border.getBorderInsets(this@setUpTopPanelMouseListeners)
          return p.x in borderInsets.left until (width - borderInsets.right) &&
            p.y in borderInsets.top until (height - borderInsets.bottom)
        }

        private fun hideToolbar() {
          sceneViewToolbar?.let { toolbar ->
            // Only hide the toolbar if the screen reader is not active
            toolbar.isVisible = ScreenReader.isActive()
          }
        }
      }

    addMouseListener(hoverTopPanelMouseListener)
    labelPanel.addMouseListener(hoverTopPanelMouseListener)
  }

  private val sceneViewBottomPanel =
    wrapPanel(sceneViewBottomBar).apply { border = JBUI.Borders.emptyTop(BOTTOM_BAR_TOP_MARGIN) }
  val sceneViewLeftPanel = wrapPanel(sceneViewLeftBar)
  val sceneViewRightPanel = wrapPanel(sceneViewRightBar)
  val sceneViewCenterPanel = wrapPanel(sceneViewErrorsPanel)

  private fun wrapPanel(panel: JComponent?) =
    JPanel(BorderLayout()).apply {
      isOpaque = false
      isVisible = true
      if (panel != null) {
        add(panel, BorderLayout.CENTER)
      }
    }

  init {
    isOpaque = false
    layout = null

    add(sceneViewTopPanel)
    add(sceneViewCenterPanel)
    add(sceneViewBottomPanel)
    add(sceneViewLeftPanel)
    add(sceneViewRightPanel)
    // This setup the initial positions of sceneViewTopPanel, sceneViewCenterPanel,
    // sceneViewBottomPanel, and sceneViewLeftPanel.
    // Otherwise they are all placed at top-left corner before first time layout.
    doLayout()
  }

  override fun isValid(): Boolean {
    return super.isValid() && layoutData.isValidFor(sceneView)
  }

  override fun doLayout() {
    layoutData = LayoutData.fromSceneView(sceneView)
    labelPanel.updateFromLayoutData(layoutData)
    labelPanel.doLayout()

    //      SceneViewPeerPanel layout:
    //
    //      |--------------------------------------------|
    //      |             sceneViewTopPanel              |    ↕ preferredHeight
    //      |---------------------------------------------
    //      |         |                       |          |    ↑
    //      |  scene  |                       |  scene   |    |
    //      |  View   |  sceneViewCenterPanel |  View    |    | centerPanelHeight
    //      |  Left   |                       |  Right   |    |
    //      |  Panel  |                       |  Panel   |    ↓
    //      |---------------------------------------------
    //      |            sceneViewBottomPanel            |    ↕ preferredHeight
    //      |---------------------------------------------
    //
    //       ←-------→                         ←--------→
    //       preferredWidth                    preferredWidth

    sceneViewTopPanel.isVisible = labelPanel.isVisible
    if (labelPanel.isVisible) {
      sceneViewTopPanel.setBounds(
        0,
        0,
        width + insets.horizontal,
        sceneViewTopPanel.preferredSize.height,
      )
    }
    val leftSectionWidth = sceneViewLeftPanel.preferredSize.width
    val centerPanelHeight =
      if (positionableAdapter.isEmptyContent()) {
        sceneViewCenterPanel.preferredSize.height
      } else {
        positionableAdapter.scaledContentSize.height
      }
    sceneViewCenterPanel.setBounds(
      leftSectionWidth,
      sceneViewTopPanel.preferredSize.height,
      width + insets.horizontal - leftSectionWidth,
      centerPanelHeight,
    )
    sceneViewBottomPanel.setBounds(
      0,
      sceneViewTopPanel.preferredSize.height + centerPanelHeight,
      width + insets.horizontal,
      sceneViewBottomPanel.preferredSize.height,
    )
    sceneViewLeftPanel.setBounds(
      0,
      sceneViewTopPanel.preferredSize.height,
      sceneViewLeftPanel.preferredSize.width,
      centerPanelHeight,
    )
    sceneViewRightPanel.setBounds(
      sceneViewLeftPanel.preferredSize.width + positionableAdapter.scaledContentSize.width,
      sceneViewTopPanel.preferredSize.height,
      sceneViewRightPanel.preferredSize.width,
      centerPanelHeight,
    )
    super.doLayout()
  }

  /** [Dimension] used to avoid extra allocations calculating [getPreferredSize] */
  override fun getPreferredSize(): Dimension =
    positionableAdapter.getScaledContentSize(cachedPreferredSize).also {
      val shouldShowCenterPanel = it.width == 0 && it.height == 0
      val width = if (shouldShowCenterPanel) sceneViewCenterPanel.preferredSize.width else it.width
      val height =
        if (shouldShowCenterPanel) sceneViewCenterPanel.preferredSize.height else it.height

      it.width = width + positionableAdapter.margin.left + positionableAdapter.margin.right
      it.height = height + positionableAdapter.margin.top + positionableAdapter.margin.bottom
    }

  override fun getMinimumSize(): Dimension {
    val shouldShowCenterPanel =
      positionableAdapter.scaledContentSize.let { it.height == 0 && it.width == 0 }
    val centerPanelWidth = if (shouldShowCenterPanel) sceneViewCenterPanel.minimumSize.width else 0
    val centerPanelHeight =
      if (shouldShowCenterPanel) sceneViewCenterPanel.minimumSize.height else 0

    return Dimension(
      maxOf(sceneViewTopPanel.minimumSize.width, SCENE_VIEW_PEER_PANEL_MIN_WIDTH, centerPanelWidth),
      sceneViewBottomPanel.preferredSize.height +
        centerPanelHeight +
        sceneViewTopPanel.minimumSize.height +
        JBUI.scale(20),
    )
  }

  override fun isVisible(): Boolean {
    return sceneView.isVisible
  }

  override fun getData(dataId: String): Any? {
    return if (SCENE_VIEW.`is`(dataId)) {
      sceneView
    } else {
      sceneView.sceneManager.model.dataContext.getData(dataId)
    }
  }

  companion object {
    /** Default initial visibility for the SceneView toolbars */
    internal var defaultToolbarVisibility = false
  }
}

private class ShowSceneViewToolbarAction : AnAction("Show SceneView Toolbars") {
  override fun actionPerformed(e: AnActionEvent) {
    SceneViewPeerPanel.defaultToolbarVisibility = true
  }
}
