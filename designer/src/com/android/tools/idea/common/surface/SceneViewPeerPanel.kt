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
import com.android.tools.idea.common.surface.sceneview.SceneViewTopPanel
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionablePanel
import com.android.tools.idea.uibuilder.surface.layout.getScaledContentSize
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.android.tools.idea.uibuilder.surface.layout.margin
import com.android.tools.idea.uibuilder.surface.layout.scaledContentSize
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Distance between bottom bound of SceneView and bottom of [SceneViewPeerPanel]. */
@SwingCoordinate private const val BOTTOM_BORDER_HEIGHT = 3

/** Minimum allowed width for the SceneViewPeerPanel. */
@SwingCoordinate private const val SCENE_VIEW_PEER_PANEL_MIN_WIDTH = 100

/**
 * A Swing component associated to the given [SceneView]. There will be one of this components in
 * the [DesignSurface] per every [SceneView] available. This panel will be positioned on the
 * coordinates of the [SceneView] and can be used to paint Swing elements on top of the [SceneView].
 */
class SceneViewPeerPanel(
  val scope: CoroutineScope,
  val sceneView: SceneView,
  private val labelPanel: JComponent,
  sceneViewStatusIconAction: AnAction?,
  sceneViewToolbarActions: List<AnAction>,
  sceneViewLeftBar: JComponent?,
  sceneViewRightBar: JComponent?,
  private val sceneViewErrorsPanel: JComponent?,
) : JPanel(), PositionablePanel, DataProvider {

  init {
    scope.launch(uiThread) {
      sceneView.sceneManager.model.organizationGroup?.isOpened?.collect { invalidate() }
    }
  }

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
            it.bottom += BOTTOM_BORDER_HEIGHT
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

  /**
   * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
   * aligned (the toolbar).
   */
  @VisibleForTesting
  val sceneViewTopPanel =
    SceneViewTopPanel(this, sceneViewStatusIconAction, sceneViewToolbarActions, labelPanel)

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
    //      |                                            |    ↕ BOTTOM_BORDER_HEIGHT
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
      BOTTOM_BORDER_HEIGHT +
        centerPanelHeight +
        sceneViewTopPanel.minimumSize.height +
        JBUI.scale(20),
    )
  }

  override fun isVisible(): Boolean {
    return sceneView.isVisible &&
      sceneView.sceneManager.model.organizationGroup?.isOpened?.value ?: true
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
