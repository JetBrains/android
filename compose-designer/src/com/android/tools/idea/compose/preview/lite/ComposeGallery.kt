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

import com.android.tools.idea.compose.preview.ComposePreviewElement
import com.android.tools.idea.compose.preview.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.GalleryTabs
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** If lite mode is enabled, one preview at a time is available with tabs to select between them. */
class ComposeGallery(content: JComponent, rootComponent: JComponent, refreshNeeded: () -> Unit) {

  private var selectedKey: PreviewElementKey? = null

  private val tabs: GalleryTabs<PreviewElementKey> =
    GalleryTabs(rootComponent, emptySet()) {
      selectedKey = it
      refreshNeeded()
    }

  /** [JPanel] that wraps tabs and content. */
  val component =
    JPanel(BorderLayout()).apply {
      add(tabs as JComponent, BorderLayout.NORTH)
      add(content, BorderLayout.CENTER)
    }

  /**
   * Update [GalleryTabs] with the list of available [ComposePreviewElementInstance].
   *
   * @return currently selected [ComposePreviewElementInstance].
   */
  fun updateAndGetSelected(
    previewElements: Sequence<ComposePreviewElement>
  ): ComposePreviewElementInstance? {
    val tabKeys = previewElements.map { element -> PreviewElementKey(element) }.toSet()
    tabs.updateKeys(tabKeys)
    selectedKey = selectedKey ?: tabKeys.firstOrNull()
    return selectedKey?.element as? ComposePreviewElementInstance
  }
}
