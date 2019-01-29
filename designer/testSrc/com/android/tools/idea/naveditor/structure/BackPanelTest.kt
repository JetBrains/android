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
package com.android.tools.idea.naveditor.structure

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.collect.ImmutableList
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class BackPanelTest : NavTestCase() {
  fun testBack() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation("root") {
        navigation("subnav", label = "sub nav") {
          navigation("subsubnav", label = "sub sub nav")
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    val sceneView = NavView(surface, surface.sceneManager!!)
    Mockito.`when`<SceneView>(surface.currentSceneView).thenReturn(sceneView)

    var callbackCalled = false
    val backPanel = BackPanel(surface, { callbackCalled = true }, project)

    var root: NlComponent = model.components[0]!!
    Mockito.`when`(surface.currentNavigation).thenReturn(root)
    surface.selectionModel.setSelection(ImmutableList.of(root))
    surface.selectionModel.clear()

    AndroidTestCase.assertFalse(backPanel.isVisible)

    root = root.getChild(0)!!
    Mockito.`when`(surface.currentNavigation).thenReturn(root)
    surface.selectionModel.setSelection(ImmutableList.of(root))
    surface.selectionModel.clear()

    AndroidTestCase.assertTrue(backPanel.isVisible)
    AndroidTestCase.assertEquals(DestinationList.ROOT_NAME, backPanel.label.text)

    backPanel.goBack()
    Mockito.verify(surface).currentNavigation = root.parent!!
    assertTrue(callbackCalled)

    root = root.getChild(0)!!
    Mockito.`when`(surface.currentNavigation).thenReturn(root)
    surface.selectionModel.setSelection(ImmutableList.of(root))
    surface.selectionModel.clear()

    AndroidTestCase.assertTrue(backPanel.isVisible)
    AndroidTestCase.assertEquals("subnav", backPanel.label.text)

    val component = Mockito.mock(NlComponent::class.java)
    Mockito.`when`(component.parent).thenReturn(null)

    Mockito.`when`(surface.currentNavigation).thenReturn(component)
    surface.selectionModel.setSelection(ImmutableList.of(component))
    surface.selectionModel.clear()

    AndroidTestCase.assertFalse(backPanel.isVisible)
  }
}