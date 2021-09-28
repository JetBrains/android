/*
 * Copyright (C) 2021 The Android Open Source Project
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
import javax.swing.AbstractButton

class ComboCheckBoxTest {

  @Test
  fun `action performed with selected items`() {
    val pool = listOf("a", "b", "c", "d", "e")
    val initialSelection = setOf("a", "d")
    var selected: Set<String> = initialSelection.toMutableSet()
    val p = ComboCheckBox.of(pool, initialSelection, { selected = it.toSet() })

    fun click(btnText: String) = TreeWalker(p).descendantStream()
      .filter { it is AbstractButton && it.text == btnText}
      .forEach { (it as AbstractButton).doClick() }

    click("Apply")
    assertThat(selected).isEqualTo(initialSelection)

    click("c")
    click("Apply")
    assertThat(selected).isEqualTo(setOf("a", "c", "d"))

    click("Select all")
    click("Apply")
    assertThat(selected).isEqualTo(setOf("a", "b", "c", "d", "e"))

    click("Deselect all")
    click("Apply")
    assertThat(selected).isEmpty()
  }

  @Test
  fun `button only enabled when there is change`() {
    var actionCount = 0
    val p = ComboCheckBox.of(listOf("a", "b", "c", "d", "e"),
                             setOf("a", "d"),
                             { ++actionCount })

    fun btn(text: String) = TreeWalker(p).descendants().filter { it is AbstractButton && it.text == text }.first() as AbstractButton

    assertThat(btn("Apply").isEnabled).isFalse()
    btn("Apply").doClick()
    assertThat(actionCount).isEqualTo(0)

    btn("c").doClick()
    assertThat(btn("Apply").isEnabled).isTrue()

    btn("Apply").doClick()
    assertThat(actionCount).isEqualTo(1)
    assertThat(btn("Apply").isEnabled).isFalse()
  }
}