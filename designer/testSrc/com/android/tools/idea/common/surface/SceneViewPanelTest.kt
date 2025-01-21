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
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class SceneViewPanelTest {

  @get:Rule val projectRule = ApplicationRule()

  @Test
  fun createPanelWithoutOrganization(): Unit = runBlocking {
    val group = OrganizationGroup("1", "1")
    val allSceneViews = (1..6).map { createSceneView(group) }
    var sceneViews = allSceneViews.toImmutableList()
    val panel =
      SceneViewPanel(
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
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 6
    }
    assertEquals(6, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    sceneViews = allSceneViews.take(5).toImmutableList()
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 5
    }
    assertEquals(5, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    sceneViews = persistentListOf()
    Disposer.dispose(panel)
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 0
    }
    assertEquals(0, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun createPanelWithOrganization(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView(),
        createSceneView(group1),
        createSceneView(group1),
        createSceneView(group2),
        createSceneView(group2),
      )
    var sceneViews = allSceneViews.toImmutableList()
    val panel =
      SceneViewPanel(
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
    panel.doLayout()

    delayUntilCondition(100, 1.seconds) { panel.positionableContent.size == 7 }
    assertEquals(5, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(2, panel.findAllDescendants<SceneViewHeader>().count())
    sceneViews = allSceneViews.drop(1).toImmutableList()
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 6
    }
    assertEquals(4, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(2, panel.findAllDescendants<SceneViewHeader>().count())
    sceneViews = persistentListOf()
    Disposer.dispose(panel)
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 0
    }
    assertEquals(0, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun groupIsRemoved(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView(group1),
        createSceneView(group1),
        createSceneView(group2),
        createSceneView(group2),
      )
    var sceneViews = allSceneViews.toImmutableList()
    val panel =
      SceneViewPanel(
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
    panel.doLayout()
    // As first scene is removed - group1 only have one element and group should not be created for
    // it.
    sceneViews = allSceneViews.drop(1).toImmutableList()
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 4
    }
    assertEquals(3, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(1, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun groupIsAdded(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val allSceneViews =
      listOf(
        createSceneView(),
        createSceneView(),
        createSceneView(),
        createSceneView(group1),
        createSceneView(group1),
      )
    var sceneViews = allSceneViews.dropLast(1).toImmutableList()
    val panel =
      SceneViewPanel(
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
    panel.doLayout()

    // There are no groups at first
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 4
    }
    assertEquals(4, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(0, panel.findAllDescendants<SceneViewHeader>().count())
    sceneViews = allSceneViews.toImmutableList()
    // One group should be created
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) {
      panel.findAllDescendants<PositionablePanel>().count() == 6
    }
    assertEquals(5, panel.findAllDescendants<SceneViewPeerPanel>().count())
    assertEquals(1, panel.findAllDescendants<SceneViewHeader>().count())
    allSceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun disableOrganization(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = true)
    val sceneViews =
      mutableListOf(
        createSceneView(group1),
        createSceneView(group1),
        createSceneView(group2),
        createSceneView(group2),
      )
    val panel =
      SceneViewPanel(
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
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 2 }
    layoutManager.setOrganization(false)
    panel.doLayout()
    // Headers are removed.
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 0 }
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun enableOrganization(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = false)
    val sceneViews =
      mutableListOf(
        createSceneView(group1),
        createSceneView(group1),
        createSceneView(group2),
        createSceneView(group2),
      )
    val panel =
      SceneViewPanel(
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
    panel.doLayout()
    layoutManager.setOrganization(true)
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 0 }
    panel.doLayout()
    // Headers are added.
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 2 }
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun changeOrganizationGroup(): Unit = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = true)
    val sceneViews = mutableListOf(createSceneView(group1), createSceneView(group2))
    val panel =
      SceneViewPanel(
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
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 0 }

    // Update organization group for sceneView[1], sceneViews now have groups - [group1, group1] -
    // one group/header should be created.
    Mockito.`when`(sceneViews[1].sceneManager.model.organizationGroup).then { group1 }
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 1 }

    // Update organization group for sceneView[0], sceneViews now have groups - [group2, group1] -
    // no group/header should be created.
    Mockito.`when`(sceneViews[0].sceneManager.model.organizationGroup).then { group2 }
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) { panel.findAllDescendants<SceneViewHeader>().count() == 0 }

    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun panelIsDisposed() = runBlocking {
    val sceneViews = (1..6).map { createSceneView(null) }
    val panel =
      SceneViewPanel(
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
    val panels = panel.findAllDescendants<SceneViewPeerPanel>()
    panels.forEach { assertTrue { it.scope.isActive } }

    Disposer.dispose(panel)
    panels.forEach { assertFalse { it.scope.isActive } }
    // There is no more SceneViewPeerPanel as panel is disposed even if provider has sceneviews
    panel.doLayout()
    delay(1000)
    assertEquals(0, panel.findAllDescendants<SceneViewPeerPanel>().count())

    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
  }

  @Test
  fun collapseGroup() = runBlocking {
    val newGroup = OrganizationGroup("new", "new")
    val oldGroup = OrganizationGroup("old", "old")
    val sceneViewWithLateUpdatedGroup = createSceneView(oldGroup)
    val sceneViews =
      mutableListOf(
        createSceneView(newGroup),
        createSceneView(newGroup),
        sceneViewWithLateUpdatedGroup,
      )
    val panel =
      SceneViewPanel(
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
    withContext(uiThread) { panel.doLayout() }
    delayUntilCondition(100, 1.seconds) { panel.positionableContent.size == 4 }

    // Update organizationGroup for sceneViewWithLateUpdatedGroup and group visibility for newGroup
    Mockito.`when`(sceneViewWithLateUpdatedGroup.sceneManager.model.organizationGroup).then {
      newGroup
    }
    newGroup.setOpened(false)
    withContext(uiThread) { panel.doLayout() }
    // Even if organizationGroup was updated later than the panel was created, visibility is still
    // updated.
    delayUntilCondition(100, 1.seconds) {
      panel.components.filterIsInstance<SceneViewPeerPanel>().any {
        it.sceneView == sceneViewWithLateUpdatedGroup
      }
    }
    val scenePanel =
      panel.components.filterIsInstance<SceneViewPeerPanel>().first {
        it.sceneView == sceneViewWithLateUpdatedGroup
      } as Component
    delayUntilCondition(100, 1.seconds) { scenePanel.isVisible == false }
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
  }

  @Test
  fun headersAreNotNullWhenGetPositionableContent() = runBlocking {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("1", "1")
    val layoutManager = TestLayoutManager(organizationEnabled = true)
    val sceneViews =
      mutableListOf(
        createSceneView(group1),
        createSceneView(group1),
        createSceneView(group2),
        createSceneView(group2),
      )
    val panel =
      SceneViewPanel(
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
    panel.doLayout()
    delayUntilCondition(100, 1.seconds) { panel.positionableContent.isNotEmpty() }
    val headers = panel.positionableContent.filterIsInstance<HeaderPositionableContent>()
    assertTrue { headers.isNotEmpty() }
    assertEquals(2, headers.size)
    sceneViews.forEach { Disposer.dispose(it.sceneManager) }
    Disposer.dispose(panel)
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

  private fun createSceneView(group: OrganizationGroup? = null): SceneView {
    val model =
      Mockito.mock(NlModel::class.java).apply {
        Mockito.`when`(this.organizationGroup).then { group }
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
