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
package com.android.tools.idea.actions

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.findActionByText
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.DesignSurfaceTestUtil.createZoomControllerFake
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurfaceSettings
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundMenuAction
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundType
import com.android.tools.idea.uibuilder.actions.DrawableScreenViewProvider
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class DrawableBackgroundMenuActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project

  @Test
  fun `switch options`() = runBlocking {
    withContext(uiThread) {
      val drawablePsiFile =
        projectRule.fixture.loadNewFile("res/drawable/icon.xml", "<drawable></drawable>")

      projectRule.fixture.openFileInEditor(drawablePsiFile.virtualFile)

      val action = DrawableBackgroundMenuAction()
      val testEvent = TestActionEvent.createTestEvent()

      assertEquals(
        """
          ✔ None
          White
          Black
          Checkered
      """
          .trimIndent(),
        prettyPrintActions(action, dataContext = testEvent.dataContext).trimIndent(),
      )

      action.findActionByText("White")!!.actionPerformed(testEvent)
      assertEquals(
        """
          None
          ✔ White
          Black
          Checkered
      """
          .trimIndent(),
        prettyPrintActions(action, dataContext = testEvent.dataContext).trimIndent(),
      )

      action.findActionByText("Black")!!.actionPerformed(testEvent)
      assertEquals(
        """
          None
          White
          ✔ Black
          Checkered
      """
          .trimIndent(),
        prettyPrintActions(action, dataContext = testEvent.dataContext).trimIndent(),
      )
    }
  }

  @Test
  fun `option change changes the global state`() = runBlocking {
    withContext(uiThread) {
      val drawablePsiFile =
        projectRule.fixture.loadNewFile("res/drawable/icon.xml", "<drawable></drawable>")

      projectRule.fixture.openFileInEditor(drawablePsiFile.virtualFile)

      val action = DrawableBackgroundMenuAction()
      val testEvent = TestActionEvent.createTestEvent()

      assertEquals(
        DrawableBackgroundType.NONE,
        DesignSurfaceSettings.getInstance(project)
          .surfaceState
          .loadDrawableBackgroundType(project, drawablePsiFile.virtualFile),
      )

      action.findActionByText("White")!!.actionPerformed(testEvent)
      assertEquals(
        DrawableBackgroundType.WHITE,
        DesignSurfaceSettings.getInstance(project)
          .surfaceState
          .loadDrawableBackgroundType(project, drawablePsiFile.virtualFile),
      )
    }
  }

  @Test
  fun `option change changes the surface state`() = runBlocking {
    withContext(uiThread) {
      val drawablePsiFile =
        projectRule.fixture.loadNewFile("res/drawable/icon.xml", "<drawable></drawable>")
      val virtualFile = drawablePsiFile.virtualFile

      projectRule.fixture.openFileInEditor(virtualFile)

      val action = DrawableBackgroundMenuAction()
      val mockDesignSurface = mock<NlDesignSurface>()
      whenever(mockDesignSurface.zoomController).thenReturn(createZoomControllerFake())
      Disposer.register(projectRule.testRootDisposable, mockDesignSurface)

      val mockLayoutlibSceneManager = mock<LayoutlibSceneManager>()
      val nlModel =
        NlModel.Builder(
            projectRule.testRootDisposable,
            AndroidBuildTargetReference.gradleOnly(projectRule.module.androidFacet!!),
            virtualFile,
            ConfigurationManager.getOrCreateInstance(projectRule.module)
              .getConfiguration(virtualFile),
          )
          .build()
      whenever(mockLayoutlibSceneManager.model).thenReturn(nlModel)
      Disposer.register(projectRule.testRootDisposable, mockLayoutlibSceneManager)

      val testDrawableScreenViewProvider = DrawableScreenViewProvider(DrawableBackgroundType.NONE)
      val screenView =
        testDrawableScreenViewProvider.createPrimarySceneView(
          mockDesignSurface,
          mockLayoutlibSceneManager,
        )
      screenView.setForceLayersRepaint(false)
      whenever(mockDesignSurface.screenViewProvider).thenReturn(testDrawableScreenViewProvider)
      val parentDataContext = DataManager.getInstance().dataContextFromFocusAsync.await()
      val testEvent =
        TestActionEvent.createTestEvent(
          SimpleDataContext.getSimpleContext(DESIGN_SURFACE, mockDesignSurface, parentDataContext)
        )

      assertEquals(
        DrawableBackgroundType.NONE,
        DesignSurfaceSettings.getInstance(project)
          .surfaceState
          .loadDrawableBackgroundType(project, virtualFile),
      )

      action.findActionByText("Checkered")!!.actionPerformed(testEvent)
      assertEquals(
        DrawableBackgroundType.CHECKERED,
        testDrawableScreenViewProvider.getDrawableBackgroudType(),
      )
    }
  }
}
