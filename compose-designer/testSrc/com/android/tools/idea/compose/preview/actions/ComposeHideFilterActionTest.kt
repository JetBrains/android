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
package com.android.tools.idea.compose.preview.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ComposeHideFilterActionTest {

  @Rule @JvmField val rule = AndroidProjectRule.inMemory()

  @Test
  fun testHideFilter() {
    val surface = mock<DesignSurface<*>>()
    val manager = TestComposePreviewManager()
    whenever(surface.getData(COMPOSE_PREVIEW_MANAGER.name)).thenReturn(manager)
    whenever(surface.sceneManagers).thenReturn(ImmutableList.of())
    manager.isFilterEnabled = true

    val action = ComposeHideFilterAction(surface)
    action.actionPerformed(TestActionEvent(DataContext.EMPTY_CONTEXT))

    assertFalse(manager.isFilterEnabled)
  }

  @Test
  fun testShowVisibleCount() {
    val surface = mock<DesignSurface<*>>()
    val manager = TestComposePreviewManager()
    whenever(surface.getData(COMPOSE_PREVIEW_MANAGER.name)).thenReturn(manager)
    val sceneManager = mock<SceneManager>()
    val sceneView1 = mock<SceneView>()
    val sceneView2 = mock<SceneView>()
    whenever(sceneManager.sceneViews).thenReturn(ImmutableList.of(sceneView1, sceneView2))
    whenever(surface.sceneManagers).thenReturn(ImmutableList.of(sceneManager))
    manager.isFilterEnabled = true

    val presentation = Presentation()
    val action = ComposeHideFilterAction(surface)

    whenever(sceneView1.isVisible).thenReturn(false)
    whenever(sceneView2.isVisible).thenReturn(false)
    action.update(TestActionEvent(presentation))
    assertEquals("no result", presentation.text)

    whenever(sceneView1.isVisible).thenReturn(true)
    action.update(TestActionEvent(presentation))
    assertEquals("1 result", presentation.text)

    whenever(sceneView2.isVisible).thenReturn(true)
    action.update(TestActionEvent(presentation))
    assertEquals("2 results", presentation.text)
  }
}
