/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.PositionablePanel
import com.android.tools.idea.common.layout.positionable.getScaledContentSize
import com.android.tools.idea.common.surface.layout.findAllScanlines
import com.android.tools.idea.common.surface.layout.findLargerScanline
import com.android.tools.idea.common.surface.layout.findSmallerScanline
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.surface.organization.SceneViewHeader
import com.android.tools.idea.common.surface.organization.createOrganizationHeader
import com.android.tools.idea.common.surface.organization.createTestOrganizationHeader
import com.android.tools.idea.common.surface.organization.findGroups
import com.android.tools.idea.common.surface.organization.paintLines
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.scene.hasValidImage
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.coroutines.CoroutineContext
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * A [JPanel] responsible for displaying [SceneView]s provided by the [sceneViewProvider].
 *
 * @param interactionLayersProvider A [Layer] provider that returns the additional interaction
 *   [Layer]s, if any
 * @param actionManagerProvider provides an [ActionManager]
 * @param shouldRenderErrorsPanel Returns true whether render error panels should be rendered when
 *   [SceneView] in this surface have render errors.
 * @param layoutManager the [PositionableContentLayoutManager] responsible for positioning and
 *   measuring the [SceneView]s
 */
class SceneViewPanel(
  private val scope: CoroutineScope,
  private val uiThreadDispatcher: CoroutineContext,
  private val sceneViewProvider: () -> Collection<SceneView>,
  private val interactionLayersProvider: () -> Collection<Layer>,
  private val actionManagerProvider: () -> ActionManager<*>,
  private val shouldRenderErrorsPanel: () -> Boolean,
  private val layoutManager: PositionableContentLayoutManager,
) : JPanel(layoutManager) {

  /**
   * Alignment for the {@link SceneView} when its size is less than the minimum size. If the size of
   * the {@link SceneView} is less than the minimum, this enum describes how to align the content
   * within the rectangle formed by the minimum size.
   */
  var sceneViewAlignment: Float = CENTER_ALIGNMENT
    set(value) {
      if (value != field) {
        field = value
        components.filterIsInstance<SceneViewPeerPanel>().forEach { it.alignmentX = value }
        repaint()
      }
    }

  /** Returns the components of this panel that are visible [PositionableContent] */
  val positionableContent: Collection<PositionableContent>
    get() =
      components
        .filterIsInstance<PositionablePanel>()
        .filter { it.isVisible() }
        .map { it.positionableAdapter }
        .toList()

  private val _organizationState: MutableSharedFlow<Unit> = MutableSharedFlow()

  /** Called if any of the [OrganizationGroup]s states has changed. */
  val organizationState: SharedFlow<Unit> = _organizationState.asSharedFlow()

  /** Called everytime when the list of [components] is updated. */
  val componentsUpdated: MutableSharedFlow<Unit> = MutableSharedFlow()

  /** True if layout supports organization. */
  private val isOrganizationEnabled = MutableStateFlow(false)

  /** List of [SceneView] to display. */
  private val sceneViews = MutableStateFlow<Collection<SceneView>>(emptyList())

  /** List of [OrganizationGroup] for [sceneViews]. */
  private val organizationGroups = MutableStateFlow<Collection<OrganizationGroup?>>(emptyList())

  /** List of [OrganizationGroup] to display. */
  private val activeGroups = MutableStateFlow<Collection<OrganizationGroup>>(emptyList())

  init {
    launchOrganizationUpdate()
    launchOrganizationStateUpdate()
    launchLayoutUpdate()
  }

  /** Listen for changes in [activeGroups] to update [organizationState]. */
  private fun launchOrganizationStateUpdate() {
    scope.launch {
      activeGroups.collectLatest {
        val isOpenedFlow = combine(it.map { it.isOpened }) {}.conflate()
        isOpenedFlow.collect {
          // Wait for the first layoutContainerFlow before calling _organizationState.
          launch {
            layoutManager.layoutContainerFlow.first().apply { _organizationState.emit(Unit) }
          }
          revalidate()
        }
      }
    }
  }

  /**
   * Listen for changes in [layoutManager] to update [isOrganizationEnabled] - if organization is
   * enabled at the moment.
   */
  private fun launchOrganizationUpdate() {
    (layoutManager as? LayoutManagerSwitcher)?.let {
      scope.launch {
        it.currentLayoutOption.collect { layoutOption ->
          isOrganizationEnabled.value = layoutOption.organizationEnabled
        }
      }
    }
  }

  /**
   * Listen for changes in [isOrganizationEnabled] and [sceneViews] to updates [activeGroups] and
   * the list of [components]. If [componets] are created - call [componentsUpdated] to let know
   * what layout is created.
   */
  private fun launchLayoutUpdate() {
    scope.launch {
      // organizationGroups are checked as SceneView is mutable, even if sceneViews is unchanged,
      // OrganizationGroup might change
      combine(isOrganizationEnabled, sceneViews, organizationGroups) { p1, p2, _ -> Pair(p1, p2) }
        .collectLatest { collected ->
          val isOrganizationEnabled = collected.first
          val sceneViews = collected.second
          activeGroups.value =
            if (isOrganizationEnabled) sceneViews.findGroups() else persistentSetOf()

          // Remove old scenes.
          val existingScenePanels = components.filterIsInstance<SceneViewPeerPanel>()
          val panelsToRemove =
            existingScenePanels.filter { panel -> !sceneViews.contains(panel.sceneView) }
          panelsToRemove.forEach { panel ->
            panel.scope.cancel()
            withContext(uiThreadDispatcher) { remove(panel) }
          }

          // Remove old headers
          val existingHeaders = components.filterIsInstance<SceneViewHeader>()
          val headerToRemove =
            existingHeaders.filter {
              !activeGroups.value.contains(it.positionableAdapter.organizationGroup)
            }
          headerToRemove.forEach { header -> withContext(uiThreadDispatcher) { remove(header) } }

          // Create or reuse scene panels.
          val orderedComponents: MutableList<Component> =
            sceneViews
              .mapIndexed { index, sceneView ->
                existingScenePanels.firstOrNull { it.sceneView == sceneView }
                  ?: withContext(uiThreadDispatcher) {
                    createScenePanel(
                      sceneView,
                      index,
                      scope.createChildScope(parentDisposable = sceneView),
                    )
                  }
              }
              .toMutableList()

          // Create or reuse headers.
          activeGroups.value
            .map { group ->
              components.filterIsInstance<SceneViewHeader>().firstOrNull {
                it.positionableAdapter.organizationGroup == group
              } ?: withContext(uiThreadDispatcher) { createHeader(group) }
            }
            .forEach { header ->
              orderedComponents
                .indexOfFirst {
                  (it as? SceneViewPeerPanel)?.positionableAdapter?.organizationGroup ==
                    header.positionableAdapter.organizationGroup
                }
                .takeIf { index -> index >= 0 }
                ?.let { index -> orderedComponents.add(index, header) }
            }

          // Set correct order to components.
          withContext(uiThreadDispatcher) {
            removeAll()
            orderedComponents.forEach { this@SceneViewPanel.add(it) }
          }

          if (orderedComponents.isNotEmpty()) {
            componentsUpdated.emit(Unit)
          }

          withContext(uiThreadDispatcher) { invalidate() }
        }
    }
  }

  /**
   * Create [SceneViewPeerPanel] for target [sceneView]
   *
   * @param index of the [sceneView] in layout
   * @param sceneScope [CoroutineScope] of the [SceneViewPeerPanel]. Should be cancelled if
   *   [SceneViewPeerPanel] is removed
   */
  @UiThread
  private suspend fun createScenePanel(
    sceneView: SceneView,
    index: Int,
    sceneScope: CoroutineScope,
  ): SceneViewPeerPanel {
    val partOfTheGroup =
      combine(activeGroups, isOrganizationEnabled, organizationGroups) {
          activeGroups,
          isOrganizationEnabled,
          _ ->
          isOrganizationEnabled &&
            activeGroups.contains(sceneView.sceneManager.model.organizationGroup)
        }
        .stateIn(sceneScope)

    return SceneViewPeerPanel(
        scope = sceneScope,
        sceneView = sceneView,
        labelPanel =
          actionManagerProvider().createSceneViewLabel(sceneView, sceneScope, partOfTheGroup),
        statusIconAction = actionManagerProvider().sceneViewStatusIconAction,
        toolbarActions = actionManagerProvider().sceneViewContextToolbarActions,
        // The left bar is only added for the first panel
        leftPanel =
          if (index == 0) actionManagerProvider().getSceneViewLeftBar(sceneView) else null,
        rightPanel = actionManagerProvider().getSceneViewRightBar(sceneView),
        errorsPanel =
          if (shouldRenderErrorsPanel()) actionManagerProvider().createErrorPanel(sceneView)
          else null,
        isOrganizationEnabled = partOfTheGroup,
      )
      .also { it.alignmentX = sceneViewAlignment }
  }

  @UiThread
  private fun createHeader(group: OrganizationGroup): SceneViewHeader {
    return SceneViewHeader(
      this@SceneViewPanel,
      group,
      if (useTestNonComposeHeaders) ::createTestOrganizationHeader else ::createOrganizationHeader,
    )
  }

  /** Use [createTestOrganizationHeader] instead of [createOrganizationHeader] if true. */
  private var useTestNonComposeHeaders = false

  /**
   * Due to issue b/346722476 in Compose for Desktop, some of FakeUI tests are failing with some of
   * the Compose for Desktop components. [setNoComposeHeadersForTests] allows to use test
   * non-compose component [createTestOrganizationHeader] instead of compose
   * [createOrganizationHeader] for these tests. Should ONLY be used if a FakeUI test is failing
   * with same b/346722476 error. The method will be removed once issue is resolved b/383713655.
   */
  @TestOnly
  fun setNoComposeHeadersForTests() {
    useTestNonComposeHeaders = true
  }

  fun updateComponents() {
    sceneViewProvider().let {
      sceneViews.value = it
      organizationGroups.value =
        it.map { sceneView -> sceneView.sceneManager.model.organizationGroup }
    }
    if (!scope.isActive) {
      removeAll()
    }
  }

  override fun doLayout() {
    updateComponents()
    super.doLayout()
  }

  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)
    val sceneViewPeerPanels = components.filterIsInstance<SceneViewPeerPanel>()

    if (sceneViewPeerPanels.isEmpty()) {
      return
    }

    findComponentGroups().paintLines(graphics.create() as Graphics2D)

    val g2d = graphics.create() as Graphics2D
    try {
      // The visible area in the editor
      val viewportBounds: Rectangle = g2d.clipBounds

      // A Dimension used to avoid reallocating new objects just to obtain the PositionableContent
      // dimensions
      val reusableDimension = Dimension()
      val positionables = positionableContent
      val horizontalTopScanLines = positionables.findAllScanlines { it.y }
      val horizontalBottomScanLines =
        positionables.findAllScanlines { it.y + it.getScaledContentSize(reusableDimension).height }
      val verticalLeftScanLines = positionables.findAllScanlines { it.x }
      val verticalRightScanLines =
        positionables.findAllScanlines { it.x + it.getScaledContentSize(reusableDimension).width }
      @SwingCoordinate val viewportRight = viewportBounds.x + viewportBounds.width
      @SwingCoordinate val viewportBottom = viewportBounds.y + viewportBounds.height
      val clipBounds = Rectangle()
      for (sceneViewPeerPanel in sceneViewPeerPanels) {
        val positionable = sceneViewPeerPanel.positionableAdapter
        val size = positionable.getScaledContentSize(reusableDimension)
        val renderErrorPanel =
          shouldRenderErrorsPanel() &&
            sceneViewPeerPanel.sceneView.hasRenderErrors() &&
            !sceneViewPeerPanel.sceneView.hasValidImage()

        @SwingCoordinate
        val right =
          positionable.x +
            if (renderErrorPanel) sceneViewPeerPanel.sceneViewCenterPanel.preferredSize.width
            else size.width

        @SwingCoordinate
        val bottom =
          positionable.y +
            if (renderErrorPanel) sceneViewPeerPanel.sceneViewCenterPanel.preferredSize.height
            else size.height
        // This finds the maximum allowed area for the screen views to paint into. See more details
        // in the ScanlineUtils.kt documentation.
        @SwingCoordinate
        var minX = findSmallerScanline(verticalRightScanLines, positionable.x, viewportBounds.x)
        @SwingCoordinate
        var minY = findSmallerScanline(horizontalBottomScanLines, positionable.y, viewportBounds.y)
        @SwingCoordinate var maxX = findLargerScanline(verticalLeftScanLines, right, viewportRight)
        @SwingCoordinate
        var maxY = findLargerScanline(horizontalTopScanLines, bottom, viewportBottom)

        // Now, (minX, minY) (maxX, maxY) describes the box that a PositionableContent could paint
        // into without painting on top of another PositionableContent render. We use this box to
        // paint the components that are outside of the rendering area.
        // However, now we need to avoid there "out of bounds" components from being on top of each
        // other. To do that, we simply find the middle point, except on the corners of the surface.
        // For example, the first PositionableContent on the left, does not have any other
        // PositionableContent that could paint on its left side so we do not need to find the
        // middle point in those cases.
        minX = if (minX > viewportBounds.x) (minX + positionable.x) / 2 else viewportBounds.x
        maxX = if (maxX < viewportRight) (maxX + right) / 2 else viewportRight
        minY = if (minY > viewportBounds.y) (minY + positionable.y) / 2 else viewportBounds.y
        maxY = if (maxY < viewportBottom) (maxY + bottom) / 2 else viewportBottom
        clipBounds.setBounds(minX, minY, maxX - minX, maxY - minY)
        g2d.clip = clipBounds
        // Only paint the scene view if it needs to be painted, otherwise the sceneViewCenterPanel
        // will be painted instead.
        if (!renderErrorPanel) {
          sceneViewPeerPanel.sceneView.paint(g2d)
        }
      }

      val interactionLayers = interactionLayersProvider()
      if (interactionLayers.isNotEmpty()) {
        // Temporary overlays do not have a clipping area
        g2d.clip = viewportBounds

        // Temporary overlays:
        interactionLayersProvider().filter { it.isVisible }.forEach { it.paint(g2d) }
      }
    } finally {
      g2d.dispose()
    }
  }

  /**
   * Find collection of component's groups. Each component group - Collection<JComponent> -
   * corrsepond to the components with same OrnanizationGroup.
   */
  private fun findComponentGroups(): Collection<Collection<JComponent>> =
    components
      .filterIsInstance<SceneViewPeerPanel>()
      .groupBy { it.positionableAdapter.organizationGroup }
      .filter { activeGroups.value.contains(it.key) }
      .values

  fun findSceneViewRectangle(sceneView: SceneView): Rectangle? =
    components
      .filterIsInstance<SceneViewPeerPanel>()
      .filter { sceneView == it.sceneView }
      .map { it.bounds }
      .firstOrNull()

  fun findSceneViewRectangles(): HashMap<SceneView, Rectangle?> =
    hashMapOf(
      *components
        .filterIsInstance<SceneViewPeerPanel>()
        .distinctBy { it.sceneView }
        .map {
          it.sceneView to it.bounds.apply { location = Point(it.sceneView.x, it.sceneView.y) }
        }
        .toTypedArray()
    )

  /**
   * Find the predicted rectangle of the [sceneView] when layout manager re-layout the content with
   * the given [availableSize].
   */
  fun findMeasuredSceneViewRectangle(sceneView: SceneView, availableSize: Dimension): Rectangle? {
    val panel =
      components.filterIsInstance<SceneViewPeerPanel>().firstOrNull { sceneView == it.sceneView }
        ?: return null

    val layoutManager = layout as PositionableContentLayoutManager
    val positions =
      layoutManager.getMeasuredPositionableContentPosition(
        positionableContent,
        availableSize.width,
        availableSize.height,
      )
    val position = positions[panel.positionableAdapter] ?: return null
    return panel.bounds.apply { location = position }
  }
}
