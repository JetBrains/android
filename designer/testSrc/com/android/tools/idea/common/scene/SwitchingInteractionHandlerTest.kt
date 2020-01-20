/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.scene

import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.SwitchingInteractionHandler
import com.google.common.truth.Truth.assertThat
import com.intellij.util.containers.enumMapOf
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import java.awt.Cursor
import java.awt.dnd.DropTargetDragEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.EnumMap

class SwitchingInteractionHandlerTest {

  private enum class Handlers {
    FIRST,
    SECOND
  }

  private class CountingInteractionHandler : InteractionHandler {
    var counter = 0

    override fun createInteractionOnPressed(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null
    override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null
    override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? = null
    override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction? = null
    override fun mouseReleaseWhenNoInteraction(x: Int, y: Int, modifiersEx: Int) { }
    override fun singleClick(x: Int, y: Int, modifiersEx: Int) { counter++ }
    override fun doubleClick(x: Int, y: Int, modifiersEx: Int) { }
    override fun hoverWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int) { }
    override fun popupMenuTrigger(mouseEvent: MouseEvent) { }
    override fun getCursorWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int): Cursor? = null
  }

  @Test
  fun testSwitch() {
    val handler1 = CountingInteractionHandler()
    val handler2 = CountingInteractionHandler()

    val handlers = EnumMap<Handlers, InteractionHandler>(Handlers::class.java)
    handlers[Handlers.FIRST] = handler1
    handlers[Handlers.SECOND] = handler2

    val handler = SwitchingInteractionHandler(handlers, Handlers.FIRST)

    handler.singleClick(0, 0, 0)

    assertThat(handler1.counter).isEqualTo(1)
    assertThat(handler2.counter).isEqualTo(0)

    handler.selected = Handlers.SECOND

    handler.singleClick(0, 0, 0)

    assertThat(handler1.counter).isEqualTo(1)
    assertThat(handler2.counter).isEqualTo(1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testNoHandlers() {
    SwitchingInteractionHandler(EnumMap<Handlers, InteractionHandler>(Handlers::class.java), Handlers.FIRST)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testInvalidSelection() {
    val handler1 = CountingInteractionHandler()

    val handlers = EnumMap<Handlers, InteractionHandler>(Handlers::class.java)
    handlers[Handlers.FIRST] = handler1

    val handler = SwitchingInteractionHandler(handlers, Handlers.FIRST)

    handler.selected = Handlers.SECOND
  }
}