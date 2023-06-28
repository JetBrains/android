/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.lite

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.SingleComposePreviewElementInstance
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent.createTestEvent
import java.awt.Component
import java.util.stream.Collectors
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeEssentialsModeTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private class TestManager : TestComposePreviewManager() {
    override var availableElements: Collection<ComposePreviewElementInstance> = emptyList()
    override var singlePreviewElementInstance: ComposePreviewElementInstance? = null
  }

  @Test
  fun selectedComponent() {
    val firstElement =
      SingleComposePreviewElementInstance.forTesting("PreviewMethod1", groupName = "GroupA")
    val composePreviewManager =
      TestManager().apply {
        availableElements =
          mutableListOf(
            firstElement,
            SingleComposePreviewElementInstance.forTesting("PreviewMethod2", groupName = "GroupA"),
            SingleComposePreviewElementInstance.forTesting("PreviewMethod3", groupName = "GroupB"),
          )
      }
    val context = MapDataContext().also { it.put(COMPOSE_PREVIEW_MANAGER, composePreviewManager) }
    val gallery = ComposeEssentialsMode(JPanel())
    val tabsToolbar = findTabs(gallery.component)
    tabsToolbar.actionGroup.update(createTestEvent(context))

    assertEquals(firstElement, gallery.selectedKey!!.element)
    assertEquals(firstElement, composePreviewManager.singlePreviewElementInstance)
  }

  private fun findTabs(parent: Component): ActionToolbarImpl =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is ActionToolbarImpl }
      .collect(Collectors.toList())
      .map { it as ActionToolbarImpl }
      .first { it.place == "Gallery Tabs" }
}
