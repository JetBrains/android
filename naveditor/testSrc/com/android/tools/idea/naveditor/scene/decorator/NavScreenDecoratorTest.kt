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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.intellij.psi.xml.XmlFile
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.Dimension
import java.awt.geom.Rectangle2D

class NavScreenDecoratorTest : NavTestCase() {
  private val decorator = object : NavScreenDecorator() {
    override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
      val rectangle = Coordinates.getSwingRectDip(SceneContext.get(), component.fillDrawRect2D(0, null))
      val image = buildImage(sceneContext, component, rectangle)!!
      list.add(DrawNavScreen(rectangle, image))
    }
  }

  fun testPreview() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1", layout = "foo")
        fragment("f2", name = "foo.Bar")
        fragment("f3", layout = "foo", name = "foo.Bar")
      }
    }

    val sceneComponent1 = SceneComponent(model.surface.scene!!, model.find("f1")!!, mock(HitProvider::class.java))
    sceneComponent1.setPosition(50, 150)
    sceneComponent1.setSize(100, 200)
    val sceneComponent2 = SceneComponent(model.surface.scene!!, model.find("f2")!!, mock(HitProvider::class.java))
    sceneComponent2.setPosition(5, 15)
    sceneComponent2.setSize(10, 20)
    val sceneComponent3 = SceneComponent(model.surface.scene!!, model.find("f3")!!, mock(HitProvider::class.java))
    sceneComponent3.setPosition(500, 1500)
    sceneComponent3.setSize(1000, 2000)

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    decorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent1)
    assertEquals(listOf(Rectangle2D.Float(50f, 150f, 100f, 200f)), displayList.commands.map { (it as DrawNavScreen).rectangle })

    displayList.clear()
    decorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent2)
    assertEquals(listOf(Rectangle2D.Float(5f, 15f, 10f, 20f)), displayList.commands.map { (it as DrawNavScreen).rectangle })

    displayList.clear()
    decorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent3)
    assertEquals(listOf(Rectangle2D.Float(500f, 1500f, 1000f, 2000f)), displayList.commands.map { (it as DrawNavScreen).rectangle })
  }

  fun testBuildImage() {
    val layoutFile = myFixture.addFileToProject("res/layout/mylayout.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                                           "<android.support.constraint.ConstraintLayout/>") as XmlFile
    val model = model("nav.xml") {
      navigation {
        fragment("f1", layout = "mylayout")
      }
    }

    val sceneComponent = SceneComponent(model.surface.scene!!, model.find("f1")!!, mock(HitProvider::class.java))
    sceneComponent.setPosition(50, 150)
    sceneComponent.setSize(100, 200)

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    val origThumbnailManager = ThumbnailManager.getInstance(myFacet)
    val thumbnailManager = mock(ThumbnailManager::class.java)
    val resultImage = RefinableImage()
    val configuration = model.surface.configuration!!
    val dimensions = Dimension(100, 200)

    // This is just so createDrawImageCommand can complete without blowing up
    doReturn(resultImage).`when`(thumbnailManager).getThumbnail(layoutFile, configuration, dimensions)

    try {
      ThumbnailManager.setInstance(myFacet, thumbnailManager)
      decorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent)
    }
    finally {
      ThumbnailManager.setInstance(myFacet, origThumbnailManager)
    }

    verify(thumbnailManager).getThumbnail(layoutFile, configuration, dimensions)
  }
}