/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.Producer
import org.junit.Test
import javax.swing.*

class TooltipComponentTest {

  /**
   * Create components with a default size - the size isn't that important, but one is needed so
   * that [FakeUi] can interact with them.
   */
  private fun <C : JComponent> createWithSize(produceComponent: () -> C): C {
    return produceComponent().apply { setSize(100, 100) }
  }

  /**
   * Produce fake displayble values, since we can't check [JComponent.isDisplayable] in unit tests.
   * If [explicitRoot] is not set, will return true iff owner has a direct parent.
   */
  private class DisplayableProducer(val owner: JComponent, val explicitRoot: JComponent? = null) : Producer<Boolean> {
    override fun produce(): Boolean {
      return if (explicitRoot == null) {
        owner.parent != null
      }
      else {
        TreeWalker.isAncestor(explicitRoot, owner)
      }
    }
  }

  @Test
  fun shouldHandleAncestorStructureChanges() {
    val root = createWithSize { JLayeredPane() }
    val owner = createWithSize { JLayeredPane() }

    root.add(owner)

    val displayableProducer = DisplayableProducer(owner)
    val tooltip = TooltipComponent.Builder(JLabel(), owner, root).setIsOwnerDisplayable(displayableProducer).build()
      .apply { registerListenersOn(owner) }

    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)

    assertThat(root.components).asList().contains(tooltip)

    root.remove(owner)
    // Wait for removal event to propagate...
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE)
    assertThat(root.components).asList().doesNotContain(tooltip)
  }

  @Test
  fun shouldNotBeAddedWhenOwnerIsNotDisplayable() {
    val root = createWithSize { JLayeredPane() }
    val intermediate = createWithSize { JLayeredPane() }
    val owner = createWithSize { JLayeredPane() }

    root.add(intermediate)
    intermediate.add(owner)

    val displayableProducer = DisplayableProducer(owner, root)
    val tooltip = TooltipComponent.Builder(JLabel(), owner, root).setIsOwnerDisplayable(displayableProducer).build()
      .apply { registerListenersOn(owner) }
    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)

    assertThat(root.components).asList().contains(tooltip)

    root.remove(intermediate)
    // Wait for removal event to propogate...
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE)
    assertThat(root.components).asList().doesNotContain(tooltip)
    assertThat(tooltip.parent).isNull()
  }
}