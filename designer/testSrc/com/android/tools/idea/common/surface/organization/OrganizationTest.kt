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
package com.android.tools.idea.common.surface.organization

import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class OrganizationTest {

  @get:Rule val projectRule = ApplicationRule()

  @Test
  fun createHeaders() = runInEdt {
    val parent = JPanel()
    val group1 = OrganizationGroup("method1", "1")
    val group2 = OrganizationGroup("method2", "2")
    val sceneViews =
      listOf(
        createSceneView(group1, "name1"),
        createSceneView(group1, "name2"),
        createSceneView(group2, "name3"),
        createSceneView(group2, "name4"),
      )
    val groups = sceneViews.findGroups()
    assertThat(groups).hasSize(2)
    val headers = groups.createOrganizationHeaders(parent)
    assertThat(headers).hasSize(2)
    assertThat(headers[group1]).isNotNull()
    assertThat(headers[group2]).isNotNull()
    sceneViews.forEach {
      Disposer.dispose(it.sceneManager)
      Disposer.dispose(it)
    }
  }

  @Test
  fun noHeaders() {
    val parent = JPanel()
    val sceneViews =
      listOf(
        createSceneView(OrganizationGroup("method1", "1"), "name1"),
        createSceneView(OrganizationGroup("method2", "2"), "name2"),
        createSceneView(OrganizationGroup("method3", "3"), "name3"),
        createSceneView(OrganizationGroup("method4", "4"), "name4"),
      )
    val groups = sceneViews.findGroups()
    assertThat(groups).isEmpty()
    val headers = groups.createOrganizationHeaders(parent)
    assertThat(headers).isEmpty()
    sceneViews.forEach {
      Disposer.dispose(it.sceneManager)
      Disposer.dispose(it)
    }
  }

  @Test
  fun nullOrganizationIsNotAGroup() {
    val parent = JPanel()
    val sceneViews =
      listOf(
        createSceneView(null, "name1"),
        createSceneView(null, "name2"),
        createSceneView(OrganizationGroup("method1", "1"), "name3"),
        createSceneView(OrganizationGroup("method2", "2"), "name4"),
      )
    val groups = sceneViews.findGroups()
    assertThat(groups).isEmpty()
    val headers = groups.createOrganizationHeaders(parent)
    assertThat(headers).isEmpty()
    sceneViews.forEach {
      Disposer.dispose(it.sceneManager)
      Disposer.dispose(it)
    }
  }

  @Test
  fun headersAreNotNullWhenGetPositionableContent() {
    val parent = JPanel()
    val group1 = OrganizationGroup("method1", "1")
    val group2 = OrganizationGroup("method2", "2")
    val sceneViews: List<JPanel> =
      listOf(
        SceneViewHeader(parent, group1) { object : JComponent() {} },
        createSceneViewPeerPanel(group1, "name1"),
        createSceneViewPeerPanel(group1, "name2"),
        SceneViewHeader(parent, group2) { object : JComponent() {} },
        createSceneViewPeerPanel(group2, "name3"),
        createSceneViewPeerPanel(group2, "name4"),
      )

    val sceneViewPanel =
      createSceneViewPanel().also { sceneViewPanel ->
        sceneViews.forEach { sceneViewPanel.add(it) }
      }

    assertTrue(sceneViewPanel.positionableContent.isNotEmpty())
    val headerPositionableContent =
      sceneViewPanel.positionableContent.filterIsInstance<HeaderPositionableContent>()
    assertTrue(headerPositionableContent.isNotEmpty())
    assertEquals(2, headerPositionableContent.size)
    sceneViews.forEach {
      if (it is SceneViewPeerPanel) {
        Disposer.dispose(it.sceneView.sceneManager)
        Disposer.dispose(it.sceneView)
      }
    }
    Disposer.dispose(sceneViewPanel)
  }

  private fun createSceneViewPeerPanel(
    organizationGroup: OrganizationGroup,
    name: String,
  ): SceneViewPeerPanel {
    val testScope = CoroutineScope(EmptyCoroutineContext)
    val sceneView = createSceneView(organizationGroup, name)
    return SceneViewPeerPanel(
      scope = testScope,
      sceneView = sceneView,
      labelPanel = object : JPanel() {},
      statusIconAction = null,
      toolbarActions = emptyList(),
      leftPanel = null,
      rightPanel = null,
      errorsPanel = null,
      isOrganizationEnabled = MutableStateFlow(true),
    )
  }

  private fun createSceneViewPanel(): SceneViewPanel =
    SceneViewPanel(
      sceneViewProvider = { emptyList() },
      interactionLayersProvider = { emptyList() },
      actionManagerProvider = { Mockito.mock() },
      shouldRenderErrorsPanel = { false },
      layoutManager = Mockito.mock(),
    )

  private fun createSceneView(organizationGroup: OrganizationGroup?, modelName: String): SceneView {

    val model =
      Mockito.mock(NlModel::class.java).apply {
        Mockito.`when`(this.organizationGroup).then { organizationGroup }
        Mockito.`when`(this.displaySettings).then {
          DisplaySettings().apply { setDisplayName(modelName) }
        }
      }
    val sceneManager =
      Mockito.mock(SceneManager::class.java).apply { Mockito.`when`(this.model).then { model } }
    return TestSceneView(100, 100, sceneManager)
  }
}
