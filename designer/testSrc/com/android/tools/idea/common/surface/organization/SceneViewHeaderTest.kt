/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface.organization

import com.android.tools.adtui.swing.FakeUi
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI.scale
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SceneViewHeaderTest {

  @get:Rule val projectRule = ApplicationRule()

  @Test
  fun actualSizeChanged() {
    invokeAndWaitIfNeeded {
      val (parent, header) = setupHeaderAndParent()
      // Initial sizes
      assertEquals(scale(270), header.size.width)
      assertEquals(scale(26), header.size.height)

      val ui = FakeUi(parent)
      parent.size = Dimension(400, 400)
      ui.layoutAndDispatchEvents()

      // Updated size after resize
      assertEquals(scale(370), header.size.width)
      assertEquals(scale(26), header.size.height)
    }
  }

  @Test
  fun positionableContentSizeStaysSame() {
    invokeAndWaitIfNeeded {
      val (parent, header) = setupHeaderAndParent()
      // Initial sizes
      assertEquals(scale(100), header.positionableAdapter.getContentSize(null).width)
      assertEquals(scale(26), header.positionableAdapter.getContentSize(null).height)

      val ui = FakeUi(parent)
      parent.size = Dimension(400, 400)
      ui.layoutAndDispatchEvents()

      // PositionableContent stays the same
      assertEquals(scale(100), header.positionableAdapter.getContentSize(null).width)
      assertEquals(scale(26), header.positionableAdapter.getContentSize(null).height)
    }
  }

  @Test
  fun scaleIs1() {
    val (_, header) = setupHeaderAndParent()
    assertEquals(1.0, header.positionableAdapter.scale)
  }

  @Test
  fun organizationGroupIsSet() {
    val (_, header) = setupHeaderAndParent()
    assertEquals("Organization", header.positionableAdapter.organizationGroup)
  }

  @Test
  fun locationIsUpdated() {
    val (_, header) = setupHeaderAndParent()
    // Initial location
    assertEquals(0, header.x)
    assertEquals(0, header.y)
    assertEquals(0, header.positionableAdapter.x)
    assertEquals(0, header.positionableAdapter.y)

    header.positionableAdapter.setLocation(22, 33)

    // Updated location location
    assertEquals(22, header.x)
    assertEquals(33, header.y)
    assertEquals(22, header.positionableAdapter.x)
    assertEquals(33, header.positionableAdapter.y)
  }

  /** Setup [SceneViewHeader] and it's parent. */
  private fun setupHeaderAndParent(): Pair<JComponent, SceneViewHeader> {
    val parent =
      JPanel().apply {
        size = Dimension(300, 300)
        layout = null
      }
    val header = SceneViewHeader(parent, "Organization", "Name") { JBLabel(it.displayName.value) }
    parent.add(header)
    return parent to header
  }
}
