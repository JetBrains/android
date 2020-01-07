/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.editor.AddDestinationMenu
import com.android.tools.idea.naveditor.editor.NavActionManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.util.Collections

class EmptyDesignerTargetTest : NavTestCase() {
  fun testEmptyDesignerTarget() {
    val model = model("nav.xml") {
      navigation("root")
    }

    val surface = model.surface as NavDesignSurface
    val view = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.focusedSceneView).thenReturn(view)
    `when`<SceneView>(surface.getSceneView(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(view)

    val actionManager = mock(NavActionManager::class.java)
    val menu = mock(AddDestinationMenu::class.java)

    `when`(surface.actionManager).thenReturn(actionManager)
    doReturn(menu).`when`(actionManager).addDestinationMenu
    `when`(actionManager.getPopupMenuActions(any())).thenReturn(DefaultActionGroup())
    // We use any ?: Collections.emptyList() below because any() returns null and Kotlin will
    // complain during the null checking
    `when`(actionManager.getToolbarActions(any(), any() ?: Collections.emptyList())).thenReturn(DefaultActionGroup())

    val scene = surface.scene!!
    val root = scene.getSceneComponent("root")!!

    scene.layout(0, scene.sceneManager.sceneView.context)

    val drawRect = root.fillDrawRect(0, null)
    val x = drawRect.x + drawRect.width / 2
    val y = drawRect.y + drawRect.height / 2

    scene.mouseDown(scene.sceneManager.sceneView.context, x, y, 0)
    scene.mouseRelease(scene.sceneManager.sceneView.context, x, y, 0)

    verify(menu).show()
  }
}