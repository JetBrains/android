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
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import java.awt.Dimension
import javax.swing.JComponent

class DesignSurfaceTest: LayoutTestCase() {

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
}

private class TestDesignSurface(project: Project, disposible: Disposable): DesignSurface(project, SelectionModel(), disposible) {
  override fun createActionHandler(): DesignSurfaceActionHandler {
    throw UnsupportedOperationException("Action handler not implemented for TestDesignSurface")
  }

  override fun getSceneScalingFactor() = 1f

  override fun createActionManager() = object: ActionManager<DesignSurface>(this) {
    override fun registerActionsShortcuts(component: JComponent, parentDisposable: Disposable?) = Unit

    override fun createPopupMenu(actionManager: com.intellij.openapi.actionSystem.ActionManager,
                                 leafComponent: NlComponent?) = DefaultActionGroup()

    override fun addActions(group: DefaultActionGroup,
                            component: NlComponent?,
                            newSelection: MutableList<NlComponent>,
                            toolbar: Boolean) = Unit
  }

  override fun createSceneManager(model: NlModel) = SyncLayoutlibSceneManager(model as SyncNlModel)

  override fun layoutContent() = Unit

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
}
