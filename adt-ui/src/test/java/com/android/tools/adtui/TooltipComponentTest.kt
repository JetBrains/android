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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import javax.swing.*

class TooltipComponentTest {

  @Test
  fun addsToTheJLayeredPaneRoot() {
    val root = JLayeredPane()
    val owner = JPanel()
    root.add(owner)

    val tooltip = TooltipComponent(JLabel(), owner)
    assertThat(root.components).asList().contains(tooltip)
  }

  @Test
  fun addsToTheSecondNodeIfTheRootIsNotLayered() {
    val root = JPanel()
    val layered = JLayeredPane()
    val owner = JPanel()
    root.add(layered)
    layered.add(owner)

    val tooltip = TooltipComponent(JLabel(), owner)
    assertThat(root.components).asList().doesNotContain(tooltip)
    assertThat(layered.components).asList().contains(tooltip)
  }

  @Test
  fun addsTotheFarthestLayeredAncestor() {
    val root = JPanel()
    val layered1 = JLayeredPane()
    val layered2 = JLayeredPane()
    val owner = JPanel()
    root.add(layered1)
    layered1.add(layered2)
    layered2.add(owner)

    val tooltip = TooltipComponent(JLabel(), owner)
    assertThat(layered1.components).asList().contains(tooltip)
    assertThat(layered2.components).asList().doesNotContain(tooltip)
  }

  @Test
  fun shouldHandleAncestorStructureChanges() {
    val root1 = JLayeredPane()
    val root2 = JLayeredPane()
    val owner = Mockito.spy(JLayeredPane()).apply {
      Mockito.doReturn(true).`when`(this).isDisplayable
    }

    root1.add(owner)
    val tooltip = TooltipComponent(JLabel(), owner)
    assertThat(root1.components).asList().contains(tooltip)

    root1.remove(owner)
    assertThat(root1.components).asList().doesNotContain(tooltip)

    root2.add(owner)
    assertThat(root2.components).asList().contains(tooltip)
  }

  @Test
  fun shouldNotBeAddedWhenOwnerIsNotDisplayable() {
    val root = JLayeredPane()
    val intermediate = JLayeredPane()
    val owner = Mockito.spy(JLayeredPane()).apply {
      Mockito.doReturn(true).`when`(this).isDisplayable
    }

    root.add(intermediate)
    intermediate.add(owner)

    val tooltip = TooltipComponent(JLabel(), owner)
    assertThat(root.components).asList().contains(tooltip)

    Mockito.doReturn(false).`when`(owner).isDisplayable
    root.remove(intermediate)
    assertThat(root.components).asList().doesNotContain(tooltip)
    assertThat(tooltip.parent).isNull()
  }

  @Test
  fun withPrefferedParent() {
    val root = JLayeredPane()
    val preffered = FakeLayeredPane()
    val owner = Mockito.spy(JLayeredPane()).apply {
      Mockito.doReturn(true).`when`(this).isDisplayable
    }

    root.add(preffered)
    preffered.add(owner)
    val tooltip = TooltipComponent(JLabel(), owner, FakeLayeredPane::class.java)
    assertThat(tooltip.parent).isEqualTo(preffered)
  }

  private class FakeLayeredPane: JLayeredPane()
}