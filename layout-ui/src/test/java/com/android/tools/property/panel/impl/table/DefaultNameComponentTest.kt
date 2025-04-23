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
package com.android.tools.property.panel.impl.table

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.property.panel.api.TableSupport
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension

class DefaultNameComponentTest {

  @Test
  fun testDoubleClick() {
    var toggleCount = 0
    val tableSupport =
      object : TableSupport {
        override fun toggleGroup() {
          toggleCount++
        }
      }
    val component = DefaultNameComponent(tableSupport)
    component.size = Dimension(500, 200)
    val ui = FakeUi(component)
    ui.mouse.doubleClick(400, 100)
    assertThat(toggleCount).isEqualTo(1)
    ui.mouse.doubleClick(400, 90)
    assertThat(toggleCount).isEqualTo(2)
  }
}
