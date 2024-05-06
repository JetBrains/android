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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.target.ComponentAssistantViewAction
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.actions.ViewAction
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.rendering.RenderTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

private class HolderViewActionPresentation : ViewActionPresentation {
  var _label: String = ""
  var _enabled: Boolean = false
  var _visible: Boolean = false
  var _icon: Icon? = null

  override fun setLabel(label: String) {
    _label = label
  }

  override fun setEnabled(enabled: Boolean) {
    _enabled = enabled
  }

  override fun setVisible(visible: Boolean) {
    _visible = visible
  }

  override fun setIcon(icon: Icon?) {
    _icon = icon
  }
}

class FakeViewEditor : ViewEditor() {
  override fun getCompileSdkVersion(): AndroidVersion? = null

  override fun getMinSdkVersion(): AndroidVersion = AndroidVersion.DEFAULT

  override fun getTargetSdkVersion(): AndroidVersion = AndroidVersion.DEFAULT

  override fun getConfiguration(): Configuration {
    TODO("Not yet implemented")
  }

  override fun getModel(): NlModel {
    TODO("Not yet implemented")
  }

  override fun getSceneBuilder(): LayoutlibSceneManager {
    TODO("Not yet implemented")
  }

  override fun getRootViews(): MutableCollection<ViewInfo> = mutableListOf()

  override fun measureChildren(
    parent: NlComponent,
    filter: RenderTask.AttributeFilter?
  ): CompletableFuture<MutableMap<NlComponent, Dimension>>? = null

  override fun getScene(): Scene {
    TODO("Not yet implemented")
  }

  override fun canInsertChildren(
    parent: NlComponent,
    children: MutableList<NlComponent>,
    index: Int
  ): Boolean = false

  override fun insertChildren(
    parent: NlComponent,
    children: MutableList<NlComponent>,
    index: Int,
    insertType: InsertType
  ) {}

  override fun moduleDependsOnAppCompat(): Boolean = false
}

class TextViewHandlerTest {
  private val textViewHandler = TextViewHandler()

  private fun getAssistantPresentationFor(
    toolsText: String?,
    text: String?
  ): HolderViewActionPresentation {
    val component = mock(NlComponent::class.java)
    `when`(component.getAttribute(eq(SdkConstants.ANDROID_URI), eq(SdkConstants.ATTR_TEXT)))
      .thenReturn(text)
    `when`(component.getAttribute(eq(SdkConstants.TOOLS_URI), eq(SdkConstants.ATTR_TEXT)))
      .thenReturn(toolsText)

    val outputActions = mutableListOf<ViewAction>()
    textViewHandler.addPopupMenuActions(mock(SceneComponent::class.java), outputActions)
    val assistantAction = outputActions.single() as ComponentAssistantViewAction
    val presentation = HolderViewActionPresentation()

    assistantAction.updatePresentation(
      presentation,
      FakeViewEditor(),
      textViewHandler,
      component,
      mutableListOf(component),
      0
    )
    return presentation
  }

  @Test
  fun sampleDataActionVisibility() {
    listOf(
        null to "",
        null to null,
        "@tools:sample/cities" to "Hello world!",
        "@tools:sample/cities" to null,
        null to "TextView",
        "" to "TextView",
      )
      .forEach { (toolsText, text) ->
        val presentation = getAssistantPresentationFor(toolsText = toolsText, text = text)
        assertTrue(
          "Sample data action should be visible for toolsText='$toolsText' text='$text'",
          presentation._visible
        )
      }

    listOf(
        "Test" to "TextView",
        "TextView" to "Hello World!",
      )
      .forEach { (toolsText, text) ->
        val presentation = getAssistantPresentationFor(toolsText = toolsText, text = text)
        assertFalse(
          "Sample data action should not be visible for toolsText='$toolsText' text='$text'",
          presentation._visible
        )
      }
  }
}
