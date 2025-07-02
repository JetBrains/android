/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.idea.common.fixtures.ModelBuilder.TestActionManager
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.PositionablePanel
import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.surface.organization.SceneViewHeader
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class SceneViewPanelTest {

  @get:Rule val projectRule = ApplicationRule()

  @Test
  fun createPanelWithoutOrganization(): Unit = runBlocking {
    val group = OrganizationGroup("1", "1")
    val allSceneViews = (1..6).map { createSceneView { group } }
    var sceneViews = allSceneViews.toImmutableList()

    val scope = TestScope()
    val panel =
      SceneViewPanel(
          scope,
          StandardTestDispatcher(scope.testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = false),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    val ui = FakeUi(panel)
    ui.render()

    runInEdtAndWait { panel.updateComponents() }
    scope.runCurrent()
    scope.advanceUntilIdle()
    assertEquals(6, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(6, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews = allSceneViews.take(5).toImmutableList()
    panel.updateComponents()
    scope.runCurrent()
    scope.advanceUntilIdle()
    assertEquals(5, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(5, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews = persistentListOf()
    scope.cancel()
    panel.updateComponents()
    scope.runCurrent()
    scope.advanceUntilIdle()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 0
    }
    assertEquals(0, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun createPanelWithOrganization(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView(),
        createSceneView { group1 },
        createSceneView { group1 },
        createSceneView { group2 },
        createSceneView { group2 },
      )
    var sceneViews = allSceneViews.toImmutableList()
    val scope = TestScope()

    val panel =
      SceneViewPanel(
          scope,
          StandardTestDispatcher(scope.testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = true),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    scope.runCurrent()
    scope.advanceUntilIdle()
    assertEquals(7, panel.positionableContent.size)
    assertEquals(5, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(2, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews = allSceneViews.drop(1).toImmutableList()
    panel.updateComponents()
    scope.runCurrent()
    scope.advanceUntilIdle()
    assertEquals(6, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(4, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(2, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews = persistentListOf()
    scope.cancel()
    panel.updateComponents()

    assertEquals(0, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun groupIsRemoved(): Unit = runTest {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView { group1 },
        createSceneView { group1 },
        createSceneView { group2 },
        createSceneView { group2 },
      )
    var sceneViews = allSceneViews.toImmutableList()
    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = true),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    // As first scene is removed - group1 only have one element and group should not be created
    // for it.
    sceneViews = allSceneViews.drop(1).toImmutableList()
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(4, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(3, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(1, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun groupIsAdded(): Unit = runTest {
    val group1 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView(),
        createSceneView(),
        createSceneView(),
        createSceneView { group1 },
        createSceneView { group1 },
      )
    var sceneViews = allSceneViews.dropLast(1).toImmutableList()
    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = true),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    // There are no groups at first
    assertEquals(4, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(4, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews = allSceneViews.toImmutableList()
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    // One group should be created
    assertEquals(6, panel.findAllDescendants<PositionablePanel>().count())
    assertEquals(5, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(1, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun disableOrganization(): Unit = runTest {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = true)
    val sceneViews =
      mutableListOf(
        createSceneView { group1 },
        createSceneView { group1 },
        createSceneView { group2 },
        createSceneView { group2 },
      )

    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = layoutManager,
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(2, panel.findAllDescendants<SceneViewHeader>().count())

    layoutManager.setOrganization(false)
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    // Headers are removed.
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun enableOrganization(): Unit = runTest {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = false)
    val sceneViews =
      mutableListOf(
        createSceneView { group1 },
        createSceneView { group1 },
        createSceneView { group2 },
        createSceneView { group2 },
      )

    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = layoutManager,
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    // No headers
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())

    // Headers are added.
    layoutManager.setOrganization(true)
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(2, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun changeOrganizationGroup(): Unit = runTest {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = true)
    val sceneViews = mutableListOf(createSceneView { group1 }, createSceneView { group2 })

    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = layoutManager,
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    // No groups should be created at first
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())

    // Update organization group for sceneView[1], sceneViews now have groups - [group1, group1] -
    // one group/header should be created.
    Mockito.`when`(sceneViews[1].sceneManager.model.organizationGroup).then { group1 }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(1, panel.findAllDescendants<SceneViewHeader>().count())

    // Update organization group for sceneView[0], sceneViews now have groups - [group2, group1] -
    // no group/header should be created.
    Mockito.`when`(sceneViews[0].sceneManager.model.organizationGroup).then { group2 }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())

    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun panelIsDisposed() = runBlocking {
    val sceneViews = (1..6).map { createSceneView() }
    val scope = TestScope()

    val panel =
      SceneViewPanel(
          scope = scope,
          StandardTestDispatcher(scope.testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = false),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    scope.runCurrent()
    scope.advanceUntilIdle()
    val panels = panel.findAllDescendants<SceneViewPeerPanel>()
    panels.forEach { assertTrue { it.scope.isActive } }

    scope.cancel()
    panels.forEach { assertFalse { it.scope.isActive } }
    // There is no more SceneViewPeerPanel as panel is disposed even if provider has sceneviews
    panel.updateComponents()
    scope.runCurrent()
    scope.advanceUntilIdle()
    assertEquals(0, panel.findAllDescendants<SceneViewPeerPanel>().count())

    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun collapseGroup() = runTest {
    val newGroup = OrganizationGroup("new", "new")
    var oldGroup = OrganizationGroup("old", "old")
    val sceneViews =
      mutableListOf(
        createSceneView { newGroup },
        createSceneView { newGroup },
        createSceneView { oldGroup },
      )

    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = true),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }

    // Check panels are already created.
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertEquals(4, panel.positionableContent.size)

    // Update organizationGroup for sceneViewWithLateUpdatedGroup and group visibility for
    // newGroup
    oldGroup = newGroup
    newGroup.setOpened(false)
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    // Even if organizationGroup was updated later than the panel was created, visibility is still
    // updated.
    val scenePanel =
      panel.components.filterIsInstance<SceneViewPeerPanel>().first {
        it.sceneView == sceneViews[2]
      } as Component
    assertFalse { scenePanel.isVisible }
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun headersAreNotNullWhenGetPositionableContent() = runTest {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = true)
    val sceneViews =
      mutableListOf(
        createSceneView { group1 },
        createSceneView { group1 },
        createSceneView { group2 },
        createSceneView { group2 },
      )

    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = layoutManager,
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()
    assertTrue { panel.positionableContent.isNotEmpty() }
    val headers = panel.positionableContent.filterIsInstance<HeaderPositionableContent>()
    assertTrue { headers.isNotEmpty() }
    assertEquals(2, headers.size)
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun createPanelWithOrganizationAndFilterPreviews(): Unit = runTest {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView { group1 },
        createSceneView { group1 },
        createSceneView { group2 },
        createSceneView { group2 },
      )
    var sceneViews = allSceneViews.toImmutableList()

    val panel =
      SceneViewPanel(
          backgroundScope,
          StandardTestDispatcher(testScheduler),
          sceneViewProvider = { sceneViews },
          interactionLayersProvider = { emptyList() },
          actionManagerProvider = { TestActionManager(Mockito.mock()) },
          shouldRenderErrorsPanel = { false },
          layoutManager = TestLayoutManager(organizationEnabled = true),
        )
        .apply {
          setNoComposeHeadersForTests()
          size = Dimension(300, 300)
        }
    panel.updateComponents()
    runCurrent()
    advanceUntilIdle()

    val headers = panel.findAllDescendants<SceneViewHeader>().toImmutableList()
    assertEquals(2, headers.count())
    assertTrue { headers[0].isVisible }
    assertTrue { headers[1].isVisible }

    // Filter out preview.
    sceneViews[0].isVisible = false
    sceneViews[1].isVisible = false

    // Only one header is visible.
    assertFalse { headers[0].isVisible }
    assertTrue { headers[1].isVisible }

    // Show just one preview again.
    sceneViews[0].isVisible = true

    assertTrue { headers[0].isVisible }
    assertTrue { headers[1].isVisible }

    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  private class TestLayoutManager(organizationEnabled: Boolean) :
    PositionableContentLayoutManager(), LayoutManagerSwitcher {

    override val currentLayoutOption =
      MutableStateFlow(
        SurfaceLayoutOption(
          "",
          { GridLayoutManager() },
          organizationEnabled,
          layoutType = SurfaceLayoutOption.LayoutType.OrganizationGrid,
        )
      )

    fun setOrganization(enabled: Boolean) {
      currentLayoutOption.value =
        SurfaceLayoutOption(
          "",
          { GridLayoutManager() },
          enabled,
          layoutType = SurfaceLayoutOption.LayoutType.OrganizationGrid,
        )
    }

    override fun layoutContainer(
      content: Collection<PositionableContent>,
      availableSize: Dimension,
    ) {}

    override fun preferredLayoutSize(
      content: Collection<PositionableContent>,
      availableSize: Dimension,
    ) = Dimension(300, 300)

    override fun getMeasuredPositionableContentPosition(
      content: Collection<PositionableContent>,
      availableWidth: Int,
      availableHeight: Int,
    ): Map<PositionableContent, Point> = emptyMap()
  }

  private fun createSceneView(groupProvider: () -> OrganizationGroup? = { null }): SceneView {
    val model =
      Mockito.mock(NlModel::class.java).apply {
        Mockito.`when`(this.organizationGroup).then { groupProvider() }
        Mockito.`when`(this.displaySettings).then {
          DisplaySettings().apply { setDisplayName("Name") }
        }
      }
    val sceneManager =
      Mockito.mock(SceneManager::class.java).apply {
        Mockito.`when`(this.model).then { model }
        Mockito.`when`(this.scene).then { Mockito.mock(Scene::class.java) }
      }
    return TestSceneView(100, 100, sceneManager)
  }
}
