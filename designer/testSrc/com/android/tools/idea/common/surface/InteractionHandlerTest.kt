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
import com.android.tools.idea.common.fixtures.MouseEventBuilder
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
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

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
    val toolbar = surface.actionManager.designSurfaceToolbar

    val otherComponent = JPanel()
    val listeners = Toolkit.getDefaultToolkit().awtEventListeners

    val enterToSurfaceEvent = MouseEventBuilder(99, 99).withComponent(surface).withId(MouseEvent.MOUSE_ENTERED).build()
    val exitFromSurfaceEvent = MouseEventBuilder(101, 101).withComponent(surface).withId(MouseEvent.MOUSE_EXITED).build()
    val enterToOtherEvent = MouseEventBuilder(101, 101).withComponent(otherComponent).withId(MouseEvent.MOUSE_ENTERED).build()
    val exitFromOtherEvent = MouseEventBuilder(99, 99).withComponent(otherComponent).withId(MouseEvent.MOUSE_EXITED).build()

    run {
      // Simulate moving mouse from surface to other
      listeners.forEach { it.eventDispatched(exitFromSurfaceEvent) }
      listeners.forEach { it.eventDispatched(enterToOtherEvent) }
      assertFalse(toolbar.parent.isVisible)
    }

    run {
      // Simulate moving mouse from other to toolbar
      listeners.forEach { it.eventDispatched(exitFromOtherEvent) }
      listeners.forEach { it.eventDispatched(enterToSurfaceEvent) }
      assertTrue(toolbar.parent.isVisible)
    }
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
                                ZoomControlsPolicy.AUTO_HIDE) {
  override fun getSelectionAsTransferable(): ItemTransferable {
    return ItemTransferable(DnDTransferItem(0, ImmutableList.of()))
  }

  override fun getFitScale(): Double = 1.0

  override fun createSceneManager(model: NlModel) = TestSceneManager(model, this).apply { updateSceneView() }

  override fun scrollToCenter(list: MutableList<NlComponent>) {}

  override fun canZoomToFit() = true

  override fun getMinScale() = 0.1

  override fun getMaxScale() = 10.0

  override fun getScrollToVisibleOffset() = Dimension()

  override fun isLayoutDisabled() = true

  override fun forceUserRequestedRefresh(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

  override fun forceRefresh(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

  override fun getSelectableComponents(): List<NlComponent> = emptyList()

  override fun isShowing(): Boolean = true

  override fun getLocationOnScreen(): Point = Point(0, 0)

  override fun getVisibleRect(): Rectangle = Rectangle(0, 0, 100, 100)

  override fun getSize(rv: Dimension?): Dimension = Dimension(100, 100)
}
