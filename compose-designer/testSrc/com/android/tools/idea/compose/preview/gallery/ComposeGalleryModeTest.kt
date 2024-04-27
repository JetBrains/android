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
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.preview.modes.PREVIEW_LAYOUT_GALLERY_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun firstSelectedComponent() {
    val firstElement = SingleComposePreviewElementInstance.forTesting("PreviewMethod1")
    val composePreviewManager =
      TestManager().apply {
        allPreviewElementsInFileFlow.value =
          mutableListOf(
            firstElement,
            SingleComposePreviewElementInstance.forTesting("PreviewMethod2"),
            SingleComposePreviewElementInstance.forTesting("PreviewMethod3"),
          )
      }

    composePreviewManager.setMode(PreviewMode.Gallery(firstElement))
    val (gallery, _) = setupGallery { composePreviewManager }
    assertEquals(firstElement, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(composePreviewManager.mode.value)
    assertEquals(PREVIEW_LAYOUT_GALLERY_OPTION, composePreviewManager.mode.value.layoutOption)
    assertEquals(firstElement, (composePreviewManager.mode.value as PreviewMode.Gallery).selected)
  }

  @Test
  fun nothingSelectedInGallery() {
    val composePreviewManager =
      TestManager().apply { allPreviewElementsInFileFlow.value = emptyList() }
    composePreviewManager.setMode(PreviewMode.Gallery(null))
    val (gallery, _) = setupGallery { composePreviewManager }
    assertNull(gallery.selectedKey)
    assertInstanceOf<PreviewMode.Gallery>(composePreviewManager.mode.value)
    assertNull((composePreviewManager.mode.value as PreviewMode.Gallery).selected)
  }

  @Test
  fun secondSelectedElement() {
    val secondElement = SingleComposePreviewElementInstance.forTesting("PreviewMethod1")
    val composePreviewManager =
      TestManager().apply {
        allPreviewElementsInFileFlow.value =
          mutableListOf(
            SingleComposePreviewElementInstance.forTesting("PreviewMethod2"),
            secondElement,
            SingleComposePreviewElementInstance.forTesting("PreviewMethod3"),
          )
      }
    composePreviewManager.setMode(PreviewMode.Gallery(secondElement))
    val (gallery, _) = setupGallery { composePreviewManager }
    assertEquals(secondElement, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(composePreviewManager.mode.value)
    assertEquals(secondElement, (composePreviewManager.mode.value as PreviewMode.Gallery).selected)
  }

  @Test
  fun selectedElementUpdated() {
    val selected = SingleComposePreviewElementInstance.forTesting("PreviewMethod1")
    val newSelected = SingleComposePreviewElementInstance.forTesting("PreviewMethod3")
    val composePreviewManager =
      TestManager().apply {
        allPreviewElementsInFileFlow.value =
          mutableListOf(
            SingleComposePreviewElementInstance.forTesting("PreviewMethod2"),
            selected,
            newSelected,
          )
      }
    composePreviewManager.setMode(PreviewMode.Gallery(selected))
    val (gallery, refresh) = setupGallery { composePreviewManager }

    assertEquals(selected, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(composePreviewManager.mode.value)
    assertEquals(selected, (composePreviewManager.mode.value as PreviewMode.Gallery).selected)

    // Update selected key
    composePreviewManager.setMode(PreviewMode.Gallery(newSelected))
    refresh()
    assertEquals(newSelected, gallery.selectedKey!!.element)
    assertEquals(newSelected, (composePreviewManager.mode.value as PreviewMode.Gallery).selected)
  }

  private fun setupGallery(
    manager: () -> ComposePreviewManager,
  ): Pair<ComposeGalleryMode, () -> Unit> {
    val gallery = ComposeGalleryMode(JPanel())
    val tabsToolbar = findTabs(gallery.component)
    val refresh = {
      val context = MapDataContext().also { it.put(COMPOSE_PREVIEW_MANAGER, manager()) }
      tabsToolbar.actionGroup.update(createTestEvent(context))
      runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
    }
    refresh()
    return Pair(gallery, refresh)
  }
}
