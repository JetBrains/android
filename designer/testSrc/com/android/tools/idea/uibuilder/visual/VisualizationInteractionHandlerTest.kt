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
import com.android.resources.ScreenOrientation
import com.android.tools.idea.common.fixtures.KeyEventBuilder
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.fixtures.MouseEventBuilder
import com.android.tools.idea.common.surface.DesignSurfaceShortcut
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.surface.interaction.PanInteraction
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.testFramework.TestActionEvent
import java.awt.event.KeyEvent
import org.intellij.lang.annotations.Language
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.intThat
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class VisualizationInteractionHandlerTest : SceneTest() {

  override fun setUp() {
    super.setUp()
    DesignerTypeRegistrar.register(LayoutFileType)
    val surface = myModel.surface
    val sceneManager = surface.getSceneManager(myModel)!!

    // Return SceneView when hover on it, null otherwise.
    val view = sceneManager.sceneViews.single()
    whenever(surface.getSceneViewAt(anyInt(), anyInt())).thenReturn(null)
    val xMatcher = intThat { view.x <= it && it <= view.x + view.scaledContentSize.width }
    val yMatcher = intThat { view.y <= it && it <= view.y + view.scaledContentSize.height }

    whenever(surface.getSceneViewAt(xMatcher, yMatcher)).thenReturn(view)
  }

  override fun tearDown() {
    DesignerTypeRegistrar.clearRegisteredTypes()
    super.tearDown()
  }

  fun testNoPopupMenuTriggerWhenNotHoveredOnSceneView() {
    val surface = myModel.surface
    val interactionHandler =
      VisualizationInteractionHandler(surface) {
        CustomModelsProvider(
          "test",
          CustomConfigurationSet("Custom", emptyList()),
          object : ConfigurationSetListener {
            override fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet) =
              Unit

            override fun onCurrentConfigurationSetUpdated() = Unit
          },
        )
      }

    val view = surface.getSceneManager(myModel)!!.sceneViews.single()
    val mouseEvent =
      MouseEventBuilder(
          view.x + view.scaledContentSize.width * 2,
          view.y + view.scaledContentSize.height * 2,
        )
        .withSource(Any())
        .build()

    val popupMenuListener = Mockito.mock(ActionPopupMenuListener::class.java)
    (ActionManager.getInstance() as ActionManagerEx).addActionPopupMenuListener(
      popupMenuListener,
      testRootDisposable,
    )

    interactionHandler.popupMenuTrigger(mouseEvent)
    Mockito.verifyNoMoreInteractions(popupMenuListener)

    // TODO(b/147799910): Also test the case which popup menu is created.
    //                    For now it is not testable in unit test because the create JComponent is
    // invisible and an exception is thrown.
  }

  fun testEnterPanModeWithPanShortcut() {
    val surface = myModel.surface
    val interactionHandler = VisualizationInteractionHandler(surface) { EmptyModelsProvider }

    val keyEvent =
      KeyEventBuilder(DesignSurfaceShortcut.PAN.keyCode, KeyEvent.CHAR_UNDEFINED).build()
    val interaction = interactionHandler.keyPressedWithoutInteraction(keyEvent)
    assertInstanceOf(interaction, PanInteraction::class.java)
  }

  fun testRemoveCustomConfigurationAction() {
    val customModelsProviders =
      CustomModelsProvider(
        "test",
        CustomConfigurationSet("Custom", emptyList()),
        object : ConfigurationSetListener {
          override fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet) =
            Unit

          override fun onCurrentConfigurationSetUpdated() = Unit
        },
      )

    customModelsProviders.addCustomConfigurationAttributes(
      CustomConfigurationAttribute(
        name = "Preview 1",
        deviceId = "pixel_xl",
        apiLevel = 34,
        orientation = ScreenOrientation.PORTRAIT,
      )
    )
    assertEquals(1, customModelsProviders.customConfigSet.customConfigAttributes.size)
    val file = myFixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    val models = customModelsProviders.createNlModels(testRootDisposable, file, myBuildTarget)
    assertEquals(2, models.size)
    val action = RemoveCustomModelAction(customModelsProviders, models[0])
    val event = TestActionEvent.createTestEvent()
    action.update(event)
    assertEquals(false, event.presentation.isEnabled)

    val action2 = RemoveCustomModelAction(customModelsProviders, models[1])
    val event2 = TestActionEvent.createTestEvent()
    action2.update(event2)
    assertEquals(true, event2.presentation.isEnabled)
    action2.actionPerformed(event2)
    assertEquals(0, customModelsProviders.customConfigSet.customConfigAttributes.size)
  }

  override fun createModel(): ModelBuilder {
    return model(
      "constraint.xml",
      component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
        .withBounds(0, 0, 2000, 2000)
        .id("@id/constraint")
        .matchParentWidth()
        .matchParentHeight(),
    )
  }
}

@Language("xml")
private const val LAYOUT_FILE_TEXT =
  """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent" />"""
