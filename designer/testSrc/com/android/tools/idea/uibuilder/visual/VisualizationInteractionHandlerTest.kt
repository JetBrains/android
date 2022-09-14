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
package com.android.tools.idea.uibuilder.visual

import com.android.AndroidXConstants
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.fixtures.KeyEventBuilder
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.fixtures.MouseEventBuilder
import com.android.tools.idea.common.surface.DesignSurfaceShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.surface.PanInteraction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.openapi.fileEditor.FileEditorManager
import org.mockito.ArgumentMatchers.intThat
import org.mockito.Mockito
import org.mockito.Mockito.anyInt
import java.awt.event.KeyEvent

class VisualizationInteractionHandlerTest : SceneTest() {

  override fun setUp() {
    super.setUp()
    val surface = myModel.surface
    val sceneManager = surface.sceneManager!!

    // Return SceneView when hover on it, null otherwise.
    val view = sceneManager.sceneView
    whenever(surface.getSceneViewAt(anyInt(), anyInt())).thenReturn(null)
    val xMatcher = intThat { view.x <= it && it <= view.x + view.scaledContentSize.width }
    val yMatcher = intThat { view.y <= it && it <= view.y + view.scaledContentSize.height }

    whenever(surface.getSceneViewAt(xMatcher, yMatcher)).thenReturn(view)
  }

  fun testDoubleClickToNavigateToFileOfPreview() {
    StudioFlags.NELE_VISUALIZATION_APPLY_CONFIG_TO_LAYOUT_EDITOR.override(false)

    val navigationManager = Mockito.mock(LayoutNavigationManager::class.java)
    registerProjectService(LayoutNavigationManager::class.java, navigationManager)
    FileEditorManager.getInstance(project).openFile(myModel.virtualFile, true, true)
    val handler = VisualizationInteractionHandler(myModel.surface) { Mockito.mock(VisualizationModelsProvider::class.java) }
    val file = myModel.virtualFile
    val view = myModel.surface.sceneManager?.sceneView!!
    handler.doubleClick(view.x + view.scaledContentSize.width, view.y + view.scaledContentSize.height, 0)
    Mockito.verify(navigationManager).pushFile(file, file)

    StudioFlags.NELE_VISUALIZATION_APPLY_CONFIG_TO_LAYOUT_EDITOR.clearOverride()
  }

  fun testNoPopupMenuTriggerWhenNotHoveredOnSceneView() {
    val surface = myModel.surface
    val interactionHandler = VisualizationInteractionHandler(surface) {
      CustomModelsProvider("test", CustomConfigurationSet("Custom", emptyList()), object : ConfigurationSetListener {
        override fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet) = Unit

        override fun onCurrentConfigurationSetUpdated() = Unit
      })
    }

    val view = surface.sceneManager!!.sceneView
    val mouseEvent = MouseEventBuilder(view.x + view.scaledContentSize.width * 2, view.y + view.scaledContentSize.height * 2)
      .withSource(Any())
      .build()

    val popupMenuListener = Mockito.mock(ActionPopupMenuListener::class.java)
    (ActionManager.getInstance() as ActionManagerEx).addActionPopupMenuListener(popupMenuListener, testRootDisposable)

    interactionHandler.popupMenuTrigger(mouseEvent)
    Mockito.verifyNoMoreInteractions(popupMenuListener)

    // TODO(b/147799910): Also test the case which popup menu is created.
    //                    For now it is not testable in unit test because the create JComponent is invisible and an exception is thrown.
  }

  fun testEnterPanModeWithPanShortcut() {
    val surface = myModel.surface
    val interactionHandler = VisualizationInteractionHandler(surface) { EmptyModelsProvider }

    val keyEvent = KeyEventBuilder(DesignSurfaceShortcut.PAN.keyCode, KeyEvent.CHAR_UNDEFINED).build()
    val interaction = interactionHandler.keyPressedWithoutInteraction(keyEvent)
    assertInstanceOf(interaction, PanInteraction::class.java)
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
                   .withBounds(0, 0, 2000, 2000)
                   .id("@id/constraint")
                   .matchParentWidth()
                   .matchParentHeight()
    )
  }
}
