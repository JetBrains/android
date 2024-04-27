/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.stdui.CommonButton
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.containers.isEmpty
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel

@RunsInEdt
class TabbedToolbarTest {
  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun componentIsAddedElement() {
    val component = JLabel("Test")
    val toolbar = TabbedToolbar(component)
    val tree = TreeWalker(toolbar)
    assertThat(tree.descendantStream().filter { it == component }.findFirst().isPresent).isTrue()
  }

  @Test
  fun tabIsAdded() {
    val component = JLabel("Test")
    val toolbar = TabbedToolbar(component)
    val selected = {}
    toolbar.addTab("First", selected)
    val tree = TreeWalker(toolbar)
    assertThat(
      tree.descendantStream().anyMatch { it.javaClass.isAssignableFrom(JLabel::class.java) && (it as JLabel).text == "First" }).isTrue()
  }

  @Test
  fun closedIsCalledWhenClicked() {
    val component = JLabel("Test")
    val toolbar = TabbedToolbar(component)
    var closed = false
    toolbar.addTab("First", { }, { closed = true })
    val tree = TreeWalker(toolbar)
    val buttonComponents = tree.descendantStream().filter {
      it.javaClass.isAssignableFrom(CommonButton::class.java) && (it as CommonButton).actionListeners.isNotEmpty()
    }
    buttonComponents.forEach { (it as CommonButton).doClick() }
    assertThat(closed).isTrue()
  }

  @Test
  fun noCloseButtonWhenNoListener() {
    val component = JLabel("Test")
    val toolbar = TabbedToolbar(component)
    toolbar.addTab("First") { }
    val tree = TreeWalker(toolbar)
    val buttonComponents = tree.descendantStream().filter {
      it.javaClass.isAssignableFrom(CommonButton::class.java) && (it as CommonButton).actionListeners.isNotEmpty()
    }
    assertThat(buttonComponents.isEmpty())
  }

  @Test
  fun iconButtonsCallbackWhenClicked() {
    val component = JLabel("Test")
    val toolbar = TabbedToolbar(component)
    var clicked = false
    toolbar.addAction(StudioIcons.Common.ADD) { clicked = true }
    val tree = TreeWalker(toolbar)
    val buttonComponents = tree.descendantStream().filter {
      it.javaClass.isAssignableFrom(CommonButton::class.java) && (it as CommonButton).actionListeners.isNotEmpty()
    }
    buttonComponents.forEach { (it as CommonButton).doClick() }
    assertThat(clicked).isTrue()
  }

  @Test
  fun `can select tab by index`() {
    var selected = "nope"
    val toolbar = TabbedToolbar(JLabel("Title"))
    toolbar.addTab("First") { selected = "First" }
    toolbar.addTab("Second") { selected = "Second" }
    toolbar.addTab("Third") { selected = "Third" }

    assertThat(toolbar.countTabs()).isEqualTo(3)
    toolbar.selectTab(1)
    assertThat(selected).isEqualTo("Second")

    toolbar.clearTabs()
    toolbar.addTab("Fourth") { selected = "Fourth" }
    toolbar.addTab("Fifth") { selected = "Fifth" }
    assertThat(toolbar.countTabs()).isEqualTo(2)
    toolbar.selectTab(1)
    assertThat(selected).isEqualTo("Fifth")
  }

  @Test
  fun `adding tab should not trigger select listener`() {
    var selected = "nope"
    val toolbar = TabbedToolbar(JLabel("Title"))
    toolbar.addTab("First") { selected = "First" }
    // Confirm that calling addTab did not trigger select listener (did not set select = "First").
    assertThat(selected).isEqualTo("nope")
  }
}