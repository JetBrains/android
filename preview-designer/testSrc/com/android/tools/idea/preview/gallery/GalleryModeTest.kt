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
package com.android.tools.idea.preview.gallery

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.GALLERY_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.PreviewElement
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import javax.swing.JPanel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GalleryModeTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val previewModeManager = CommonPreviewModeManager()

  @Test
  fun firstSelectedComponent() {
    val firstElement = SingleComposePreviewElementInstance.forTesting<Unit>("PreviewMethod1")
    val previewFlowManager =
      previewFlowManagerFor(
        listOf(
          firstElement,
          SingleComposePreviewElementInstance.forTesting("PreviewMethod2"),
          SingleComposePreviewElementInstance.forTesting("PreviewMethod3"),
        )
      )

    previewModeManager.setMode(PreviewMode.Gallery(firstElement))
    val (gallery, _) = setupGallery { previewFlowManager }
    assertEquals(firstElement, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(previewModeManager.mode.value)
    assertEquals(GALLERY_LAYOUT_OPTION, previewModeManager.mode.value.layoutOption)
    assertEquals(firstElement, (previewModeManager.mode.value as PreviewMode.Gallery).selected)
  }

  @Test
  fun nothingSelectedInGallery() {
    val previewFlowManager = previewFlowManagerFor(emptyList())

    previewModeManager.setMode(PreviewMode.Gallery(null))
    val (gallery, _) = setupGallery { previewFlowManager }
    assertNull(gallery.selectedKey)
    assertInstanceOf<PreviewMode.Gallery>(previewModeManager.mode.value)
    assertNull((previewModeManager.mode.value as PreviewMode.Gallery).selected)
  }

  @Test
  fun secondSelectedElement() {
    val secondElement = SingleComposePreviewElementInstance.forTesting<Unit>("PreviewMethod2")
    val previewFlowManager =
      previewFlowManagerFor(
        listOf(
          SingleComposePreviewElementInstance.forTesting("PreviewMethod1"),
          secondElement,
          SingleComposePreviewElementInstance.forTesting("PreviewMethod3"),
        )
      )

    previewModeManager.setMode(PreviewMode.Gallery(secondElement))
    val (gallery, _) = setupGallery { previewFlowManager }
    assertEquals(secondElement, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(previewModeManager.mode.value)
    assertEquals(secondElement, (previewModeManager.mode.value as PreviewMode.Gallery).selected)
  }

  @Test
  fun selectedElementUpdated() {
    val selected = SingleComposePreviewElementInstance.forTesting<Unit>("PreviewMethod1")
    val newSelected = SingleComposePreviewElementInstance.forTesting<Unit>("PreviewMethod3")
    val previewFlowManager =
      previewFlowManagerFor(
        listOf(
          selected,
          SingleComposePreviewElementInstance.forTesting("PreviewMethod2"),
          newSelected,
        )
      )

    previewModeManager.setMode(PreviewMode.Gallery(selected))
    val (gallery, refresh) = setupGallery { previewFlowManager }

    assertEquals(selected, gallery.selectedKey!!.element)
    assertInstanceOf<PreviewMode.Gallery>(previewModeManager.mode.value)
    assertEquals(selected, (previewModeManager.mode.value as PreviewMode.Gallery).selected)

    // Update selected key
    previewModeManager.setMode(PreviewMode.Gallery(newSelected))
    refresh()
    assertEquals(newSelected, gallery.selectedKey!!.element)
    assertEquals(newSelected, (previewModeManager.mode.value as PreviewMode.Gallery).selected)
  }

  private fun setupGallery(
    previewFlowManager: () -> PreviewFlowManager<PreviewElement<*>>
  ): Pair<GalleryMode, () -> Unit> {
    val gallery = GalleryMode(JPanel())
    val tabsToolbar = findGalleryTabs(gallery.component)
    val refresh = {
      val context =
        MapDataContext().also {
          it.put(PreviewModeManager.KEY, previewModeManager)
          it.put(PreviewFlowManager.KEY, previewFlowManager())
        }
      tabsToolbar.actionGroup.update(createTestEvent(context))
      runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
    }
    refresh()
    return Pair(gallery, refresh)
  }

  private fun previewFlowManagerFor(previewElements: Collection<PreviewElement<*>>) =
    object : PreviewFlowManager<PreviewElement<*>> {
      override val allPreviewElementsFlow =
        MutableStateFlow(FlowableCollection.Present(previewElements))

      override val filteredPreviewElementsFlow = MutableStateFlow(FlowableCollection.Uninitialized)
      override val availableGroupsFlow = MutableStateFlow<Set<PreviewGroup.Named>>(setOf())
      override var groupFilter: PreviewGroup = PreviewGroup.All

      override fun setSingleFilter(previewElement: PreviewElement<*>?) {}
    }
}
