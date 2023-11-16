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
package com.android.tools.idea.compose.preview.gallery

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.PreviewMode
import com.android.tools.idea.compose.preview.SingleComposePreviewElementInstance
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

class ComposeGalleryModeTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private class TestManager : TestComposePreviewManager() {
    override val allPreviewElementsInFileFlow:
      MutableStateFlow<Collection<ComposePreviewElementInstance>> =
      MutableStateFlow(emptySet())
  }

  @Test
  fun selectedComponent() {
    val firstElement =
      SingleComposePreviewElementInstance.forTesting("PreviewMethod1", groupName = "GroupA")
    val composePreviewManager =
      TestManager().apply {
        allPreviewElementsInFileFlow.value =
          mutableListOf(
            firstElement,
            SingleComposePreviewElementInstance.forTesting("PreviewMethod2", groupName = "GroupA"),
            SingleComposePreviewElementInstance.forTesting("PreviewMethod3", groupName = "GroupB"),
          )
      }
    val context = MapDataContext().also { it.put(COMPOSE_PREVIEW_MANAGER, composePreviewManager) }
    val gallery = ComposeGalleryMode(JPanel())
    val tabsToolbar = findTabs(gallery.component)
    tabsToolbar.actionGroup.update(createTestEvent(context))
    runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }

    assertEquals(firstElement, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(composePreviewManager.mode)
    assertEquals(firstElement, (composePreviewManager.mode as PreviewMode.Gallery).selected)
  }
}
