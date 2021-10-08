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
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

class HorizontalScrollViewTest {

  @Test
  fun `arrows invisible when enough room`() {
    val content = JPanel()
    val view = HorizontalScrollView(content)

    assertThat(view.leftButton.isVisible).isFalse()
    assertThat(view.rightButton.isVisible).isFalse()
  }

  @Test
  fun `arrows visible when not enough room and can scroll more`() {
    val content = JPanel(FlowLayout()).apply { size = Dimension(100, 10) }
    val view = HorizontalScrollView(content).apply { size = Dimension(20, 10) }
    assertThat(view.leftButton.isVisible).isFalse()
    assertThat(view.rightButton.isVisible).isTrue()

    view.rightButton.doClick()
    assertThat(view.leftButton.isVisible).isTrue()

    view.scrollTo(content.width)
    assertThat(view.rightButton.isVisible).isFalse()
  }
}