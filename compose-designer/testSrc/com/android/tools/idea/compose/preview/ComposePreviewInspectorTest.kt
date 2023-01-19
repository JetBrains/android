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
package com.android.tools.idea.compose.preview

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import java.awt.Point
import javax.swing.JPanel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito

class ComposePreviewInspectorTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    StudioFlags.NELE_DP_SIZED_PREVIEW.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_DP_SIZED_PREVIEW.clearOverride()
  }

  @Test
  fun testFindHoveredComposeViewInfo() {
    val surface = mock<DesignSurface<*>>()
    val sceneView = mock<SceneView>()
    val sceneManager = mock<SceneManager>()
    whenever(sceneView.scale).thenReturn(1.0)
    whenever(sceneView.sceneManager).thenReturn(sceneManager)
    whenever(sceneManager.sceneScalingFactor).thenReturn(1f)

    val interactionPane = mock<JPanel>()
    whenever(interactionPane.locationOnScreen).thenReturn(Point())
    whenever(surface.getSceneViewAt(anyInt(), anyInt())).thenReturn(sceneView)
    whenever(surface.interactionPane).thenReturn(interactionPane)

    val manager = TestComposePreviewManager()
    manager.isInspectionTooltipEnabled = true
    whenever(surface.getData(COMPOSE_PREVIEW_MANAGER.name)).thenReturn(manager)

    val verifier = mock<(List<ComposeViewInfo>, Int, Int) -> Unit>()

    val composeViewInfo = createViewInfo()
    val handler = ComposePreviewInspector(surface, { listOf(composeViewInfo) }, verifier)

    handler.inspect(50, 50)
    Mockito.verify(verifier).invoke(listOf(composeViewInfo.children[0]), 50, 50)

    handler.inspect(150, 150)
    Mockito.verify(verifier).invoke(listOf(composeViewInfo.children[1]), 150, 150)

    handler.inspect(350, 250)
    Mockito.verify(verifier).invoke(listOf(composeViewInfo.children[1].children[0]), 350, 250)

    handler.inspect(800, 250)
    Mockito.verify(verifier).invoke(listOf(composeViewInfo.children[2]), 800, 250)

    handler.inspect(900, 380)
    Mockito.verify(verifier).invoke(listOf(composeViewInfo), 900, 380)
  }

  private fun createViewInfo(): ComposeViewInfo {
    return ComposeViewInfo(
      TestSourceLocation("root"),
      PxBounds(0, 0, 1000, 400),
      children =
        listOf(
          ComposeViewInfo(
            TestSourceLocation("child0"),
            PxBounds(0, 0, 200, 100),
            children = listOf()
          ),
          ComposeViewInfo(
            TestSourceLocation("child1"),
            PxBounds(100, 100, 500, 300),
            children =
              listOf(
                ComposeViewInfo(
                  TestSourceLocation("child1.0"),
                  PxBounds(250, 250, 500, 300),
                  children = listOf()
                )
              )
          ),
          ComposeViewInfo(
            TestSourceLocation("child2"),
            PxBounds(400, 200, 1000, 300),
            children = listOf()
          )
        )
    )
  }
}

private data class TestSourceLocation(
  override val className: String,
  override val methodName: String = "",
  override val fileName: String = "",
  override val lineNumber: Int = -1,
  override val packageHash: Int = -1
) : SourceLocation
