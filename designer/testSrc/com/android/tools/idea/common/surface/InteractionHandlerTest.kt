/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.TestSceneManager
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import javax.swing.JComponent

class InteractionHandlerTest {

  @Rule
  @JvmField
  val rule = AndroidProjectRule.inMemory()

  @Test
  fun testDesignSurfaceToolbarVisibility() {
    val handlerProvider: Function<DesignSurface<SceneManager>, InteractionHandler> = Function {
      object : InteractionHandlerBase(it) {
        override fun createInteractionOnPressed(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null
        override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null
      }
    }

    val actionManagerProvider: Function<DesignSurface<SceneManager>, ActionManager<out DesignSurface<in SceneManager>>> = Function {
      object : ActionManager<DesignSurface<SceneManager>>(it) {
        override fun registerActionsShortcuts(component: JComponent) = Unit

        override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup = DefaultActionGroup()

        override fun getToolbarActions(selection: MutableList<NlComponent>): DefaultActionGroup = DefaultActionGroup()
      }
    }

    val surface = Surface(rule.project, rule.testRootDisposable, handlerProvider, actionManagerProvider)
    val handler = handlerProvider.apply(surface)
    val toolbar = surface.actionManager.designSurfaceToolbar

    handler.hoverWhenNoInteraction(0, 0, 0)
    assertTrue(toolbar.isVisible)
    handler.mouseExited()
    assertFalse(toolbar.isVisible)
    handler.hoverWhenNoInteraction(0, 0, 0)
    assertTrue(toolbar.isVisible)
  }
}

private class Surface(project: Project, disposable: Disposable,
                      interact: Function<DesignSurface<SceneManager>, InteractionHandler>,
                      actionManager: Function<DesignSurface<SceneManager>, ActionManager<out DesignSurface<in SceneManager>>>)
  : DesignSurface<SceneManager>(project,
                                disposable,
                                actionManager,
                                interact,
                                Function { TestLayoutManager(it) },
                                Function { TestActionHandler(it) },
                                ZoomControlsPolicy.VISIBLE) {
  override fun getSelectionAsTransferable(): ItemTransferable {
    return ItemTransferable(DnDTransferItem(0, ImmutableList.of()))
  }

  override fun createSceneManager(model: NlModel) = TestSceneManager(model, this).apply { updateSceneView() }

  override fun scrollToCenter(list: MutableList<NlComponent>) {}

  override fun canZoomToFit() = true

  override fun getMinScale() = 0.1

  override fun getMaxScale() = 10.0

  override fun getDefaultOffset() = Dimension()

  override fun getPreferredContentSize(availableWidth: Int, availableHeight: Int) = Dimension()

  override fun isLayoutDisabled() = true

  override fun forceUserRequestedRefresh(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

  override fun forceRefresh(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

  override fun getSelectableComponents(): List<NlComponent> = emptyList()
}
