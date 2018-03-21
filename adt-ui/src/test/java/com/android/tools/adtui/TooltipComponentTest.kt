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
  fun addsToTheJLayeredPaneRoot() {
    val root = createWithSize { JLayeredPane() }
    val owner = createWithSize { JPanel() }
    root.add(owner)

    val displayableProducer = DisplayableProducer(owner)
    val tooltip = TooltipComponent(JLabel(), owner, null, displayableProducer).apply { registerListenersOn(owner) }

    // Tooltip only added when mouse moves over [owner]
    assertThat(root.components).asList().doesNotContain(tooltip)

    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)

    assertThat(root.components).asList().contains(tooltip)

    // Tooltip removed on mouse exit
    fakeUi.mouse.moveTo(-50, -50)
    assertThat(root.components).asList().doesNotContain(tooltip)
  }

  @Test
  fun addsToTheSecondNodeIfTheRootIsNotLayered() {
    val root = createWithSize { JPanel() }
    val layered = createWithSize { JLayeredPane() }
    val owner = createWithSize { JPanel() }
    root.add(layered)
    layered.add(owner)

    val displayableProducer = DisplayableProducer(owner)
    val tooltip = TooltipComponent(JLabel(), owner, null, displayableProducer).apply { registerListenersOn(owner) }
    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)

    assertThat(root.components).asList().doesNotContain(tooltip)
    assertThat(layered.components).asList().contains(tooltip)
  }

  @Test
  fun addsTotheFarthestLayeredAncestor() {
    val root = createWithSize { JPanel() }
    val layered1 = createWithSize { JLayeredPane() }
    val layered2 = createWithSize { JLayeredPane() }
    val owner = createWithSize { JPanel() }
    root.add(layered1)
    layered1.add(layered2)
    layered2.add(owner)

    val displayableProducer = DisplayableProducer(owner)
    val tooltip = TooltipComponent(JLabel(), owner, null, displayableProducer).apply { registerListenersOn(owner) }
    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)

    assertThat(layered1.components).asList().contains(tooltip)
    assertThat(layered2.components).asList().doesNotContain(tooltip)
  }

  @Test
  fun shouldHandleAncestorStructureChanges() {
    val root1 = createWithSize { JLayeredPane() }
    val root2 = createWithSize { JLayeredPane() }
    val owner = createWithSize { JLayeredPane() }

    val displayableProducer = DisplayableProducer(owner)
    val tooltip = TooltipComponent(JLabel(), owner, null, displayableProducer).apply { registerListenersOn(owner) }

    root1.add(owner)
    val fakeUi1 = FakeUi(root1)
    fakeUi1.mouse.moveTo(50, 50)

    assertThat(root1.components).asList().contains(tooltip)

    root1.remove(owner)
    // Wait for removal event to propogate...
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE)
    assertThat(root1.components).asList().doesNotContain(tooltip)

    root2.add(owner)
    val fakeUi2 = FakeUi(root2)
    fakeUi2.mouse.moveTo(50, 50)
    assertThat(root2.components).asList().contains(tooltip)
  }

  @Test
  fun shouldNotBeAddedWhenOwnerIsNotDisplayable() {
    val root = createWithSize { JLayeredPane() }
    val intermediate = createWithSize { JLayeredPane() }
    val owner = createWithSize { JLayeredPane() }

    root.add(intermediate)
    intermediate.add(owner)

    val displayableProducer = DisplayableProducer(owner, root)
    val tooltip = TooltipComponent(JLabel(), owner, null, displayableProducer).apply { registerListenersOn(owner) }
    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)

    assertThat(root.components).asList().contains(tooltip)

    root.remove(intermediate)
    // Wait for removal event to propogate...
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE)
    assertThat(root.components).asList().doesNotContain(tooltip)
    assertThat(tooltip.parent).isNull()
  }

  @Test
  fun withPreferredParent() {
    val root = createWithSize { JLayeredPane() }
    val preferred = createWithSize { FakeLayeredPane() }
    val owner = createWithSize { JLayeredPane() }

    root.add(preferred)
    preferred.add(owner)
    val displayableProducer = DisplayableProducer(owner)
    val tooltip = TooltipComponent(JLabel(), owner, FakeLayeredPane::class.java, displayableProducer).apply { registerListenersOn(owner) }

    val fakeUi = FakeUi(root)
    fakeUi.mouse.moveTo(50, 50)
    assertThat(tooltip.parent).isEqualTo(preferred)
  }

  private class FakeLayeredPane: JLayeredPane()
}