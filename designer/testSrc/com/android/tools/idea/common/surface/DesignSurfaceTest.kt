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
package com.android.tools.idea.common.surface

import com.android.SdkConstants.*
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage
import java.util.function.Consumer
import javax.swing.JComponent

class DesignSurfaceTest : LayoutTestCase() {

  fun testAddAndRemoveModel() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).build()
    val model2 = model("model2.xml", component(CONSTRAINT_LAYOUT.oldName())).build()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.addModel(model1)
    assertEquals(1, surface.models.size)

    surface.addModel(model2)
    assertEquals(2, surface.models.size)

    surface.removeModel(model2)
    assertEquals(1, surface.models.size)

    surface.removeModel(model1)
    assertEquals(0, surface.models.size)
  }

  fun testAddDuplicatedModel() {
    val model = model("model.xml", component(RELATIVE_LAYOUT)).build()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.addModel(model)
    assertEquals(1, surface.models.size)

    surface.addModel(model)
    // should not add model again and the callback should not be triggered.
    assertEquals(1, surface.models.size)
  }

  fun testRemoveIllegalModel() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).build()
    val model2 = model("model2.xml", component(RELATIVE_LAYOUT)).build()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.removeModel(model1)
    // do nothing and the callback should not be triggered.
    assertEquals(0, surface.models.size)

    surface.addModel(model1)
    assertEquals(1, surface.models.size)

    surface.removeModel(model2)
    assertEquals(1, surface.models.size)
  }

  fun testScale() {
    val surface = TestDesignSurface(myModule.project, myModule.project)
    surface.setScale(0.66, -1, -1)
    assertFalse(surface.setScale(0.663, -1, -1))
    assertFalse(surface.setScale(0.664, -1, -1))
    assertTrue(surface.setScale(0.665, -1, -1))

    surface.sceneScalingFactor = 2f

    surface.setScale(0.33, -1, -1)
    assertFalse(surface.setScale(0.332, -1, -1))
    assertTrue(surface.setScale(0.335, -1, -1))
  }
}

private class TestDesignSurface(project: Project, disposible: Disposable) : DesignSurface(project, SelectionModel(), disposible) {

  private var factor: Float = 1f

  override fun getComponentRegistrar() = Consumer<NlComponent> {}

  override fun createActionHandler(): DesignSurfaceActionHandler {
    throw UnsupportedOperationException("Action handler not implemented for TestDesignSurface")
  }

  fun setSceneScalingFactor(factor: Float) {
    this.factor = factor
  }

  override fun getSceneScalingFactor() = factor

  override fun createActionManager() = object : ActionManager<DesignSurface>(this) {
    override fun registerActionsShortcuts(component: JComponent, parentDisposable: Disposable?) = Unit

    override fun getPopupMenuActions(leafComponent: NlComponent?) = DefaultActionGroup()

    override fun getToolbarActions(component: NlComponent?, newSelection: MutableList<NlComponent>) = DefaultActionGroup()
  }

  override fun createSceneManager(model: NlModel) = SyncLayoutlibSceneManager(model as SyncNlModel)

  override fun getRenderableBoundsOfSceneView(sceneView: SceneView, rectangle: Rectangle?): Rectangle {
    val rect = rectangle ?: Rectangle()
    rect.bounds = myScrollPane.viewport.viewRect
    return rect
  }

  override fun layoutContent() = Unit

  override fun scrollToCenter(list: MutableList<NlComponent>) {}

  override fun getScrolledAreaSize(): Dimension? = null

  override fun getContentOriginX() = 0

  override fun getContentOriginY() = 0

  override fun getContentSize(dimension: Dimension?) = Dimension()

  override fun getDefaultOffset() = Dimension()

  override fun getPreferredContentSize(availableWidth: Int, availableHeight: Int) = Dimension()

  override fun isLayoutDisabled() = true

  override fun doCreateInteractionOnClick(mouseX: Int, mouseY: Int, view: SceneView) = null

  override fun createInteractionOnDrag(draggedSceneComponent: SceneComponent, primary: SceneComponent?) = null

  override fun forceUserRequestedRefresh() = Unit

  override fun getSelectableComponents(): List<NlComponent> = emptyList()
}
