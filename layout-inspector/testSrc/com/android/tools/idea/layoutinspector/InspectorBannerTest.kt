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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.StatusNotificationImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.junit.Test
import java.awt.Dimension

class InspectorBannerTest {

  @Test
  fun testInitiallyHidden() {
    val model = model { view(ROOT, 1, 2, 3, 4, "rootType") }
    val banner = InspectorBanner(model)
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testVisibleWithStatus() {
    val model = model { view(ROOT, 1, 2, 3, 4, "rootType") }
    val banner = InspectorBanner(model)
    model.notification = StatusNotificationImpl("There is an error somewhere", emptyList())
    assertThat(banner.isVisible).isTrue()
  }

  @Test
  fun testInvisibleAfterEmptyStatus() {
    val model = model { view(ROOT, 1, 2, 3, 4, "rootType") }
    val banner = InspectorBanner(model)
    model.notification = StatusNotificationImpl("There is an error somewhere", emptyList())
    model.notification = null
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testMessageShortenedOnLimitedSpace() {
    val model = model { view(ROOT, 1, 2, 3, 4, "rootType") }
    val banner = InspectorBanner(model)
    val label = banner.getComponent(0)
    val action = object: AnAction("Fix") {
      override fun actionPerformed(event: AnActionEvent) {}
    }
    model.notification = StatusNotificationImpl("There is an error somewhere", listOf(action))
    banner.size = banner.preferredSize
    banner.doLayout()
    assertThat(label.size).isEqualTo(label.preferredSize)
    banner.size = Dimension(banner.preferredSize.width - 80, banner.preferredSize.height)
    banner.doLayout()
    assertThat(label.size).isEqualTo(Dimension(label.preferredSize.width - 80, label.preferredSize.height))
  }
}
