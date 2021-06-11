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
    var selected = setOf<String>()
    val p = ComboCheckBox.of(listOf("a", "b", "c", "d", "e"),
                             setOf("a", "d"),
                             { selected = it.toSet() })

    fun click(btnText: String) = TreeWalker(p).descendantStream()
      .filter { it is AbstractButton && it.text == btnText}
      .forEach { (it as AbstractButton).doClick() }

    click("Apply")
    assertThat(selected).isEqualTo(setOf("a", "d"))

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
}