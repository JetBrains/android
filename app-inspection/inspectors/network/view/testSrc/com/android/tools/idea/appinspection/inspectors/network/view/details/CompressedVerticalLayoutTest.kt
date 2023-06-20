/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import org.junit.Test

class CompressedVerticalLayoutTest {
  @Test
  fun testCompressedVerticalLayout() {
    val container = JPanel(HttpDataComponentFactory.CompressedVerticalLayout())
    container.setBounds(0, 0, 40, Short.MAX_VALUE.toInt())
    val component = CustomVerticalComponent(60, 90, 20, 30)
    container.add(FixedComponent())
    container.add(component)
    container.add(FixedComponent())
    container.doLayout()
    assertThat(component.bounds).isEqualTo(Rectangle(0, 0, 20, 30))
  }

  private class FixedComponent : JComponent() {
    override fun getMaximumSize() = Dimension(30, 10)
    override fun isMaximumSizeSet() = true
    override fun getPreferredSize() = Dimension(30, 10)
    override fun isPreferredSizeSet() = true
    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      assertThat(x).isEqualTo(0)
      assertThat(y).isEqualTo(0)
      assertThat(width).isEqualTo(30)
      assertThat(height).isEqualTo(10)
      super.setBounds(x, y, width, height)
    }
  }

  private class CustomVerticalComponent(
    private var maxWidth: Int,
    private var maxHeight: Int,
    preferredWidth: Int,
    preferredHeight: Int
  ) : JComponent() {
    private var _preferredWidth = preferredWidth
      set(value) {
        field = value
        assertThat(maxWidth).isAtLeast(field)
      }
    private var _preferredHeight = preferredHeight
      set(value) {
        field = value
        assertThat(maxHeight).isAtLeast(_preferredHeight)
      }
    private var resizeCalls = 0

    override fun getPreferredSize() = Dimension(_preferredWidth, _preferredHeight)

    override fun isPreferredSizeSet() = true

    override fun getMaximumSize() = Dimension(maxWidth, maxHeight)

    override fun isMaximumSizeSet() = true

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      assertThat(x).isEqualTo(0)
      assertThat(y).isEqualTo(0)
      if (resizeCalls % 2 == 0) {
        assertThat(width).isAtMost(maxWidth)
        assertThat(height).isEqualTo(maxHeight)
      } else {
        assertThat(width).isEqualTo(_preferredWidth)
        assertThat(height).isEqualTo(_preferredHeight)
      }
      resizeCalls++
      super.setBounds(x, y, width, height)
    }
  }
}
