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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.layout.SKIP_PERSISTED_LAYOUT
import com.android.tools.idea.naveditor.surface.NavDesignSurfaceZoomController
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.concurrent.CompletableFuture
import junit.framework.TestCase
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutoArrangeActionTest : NavTestCase() {
  fun testAction() {
    val model =
      model("nav.xml") {
        NavModelBuilderUtil.navigation {
          fragment("f1")
          fragment("f2")
          fragment("f3")
        }
      }
    val surface = model.surface
    val scene = surface.scene!!
    val root = scene.root!!
    val manager = spy(surface.getSceneManager(model))!!
    doAnswer {
        root.children.forEach { component ->
          TestCase.assertEquals(
            true,
            component.nlComponent.getClientProperty(SKIP_PERSISTED_LAYOUT),
          )
        }
        CompletableFuture.completedFuture(null)
      }
      .whenever(manager)
      .requestRender()

    whenever(surface.getSceneManager(any())).thenReturn(manager)
    whenever(surface.zoomController).thenReturn(mock<NavDesignSurfaceZoomController>())
    val actionEvent = mock<AnActionEvent>()
    whenever(actionEvent.getData(DESIGN_SURFACE)).thenReturn(surface)
    AutoArrangeAction.instance.actionPerformed(actionEvent)
    root.children.forEach { component ->
      TestCase.assertNull(component.nlComponent.getClientProperty(SKIP_PERSISTED_LAYOUT))
    }
    verify(manager).requestRender()
    verify(surface.zoomController).zoomToFit()
  }
}
