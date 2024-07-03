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

import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.findPreviewManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataContext
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.TestOnly

/**
 * If Gallery mode is enabled, one preview at a time is available with dropdown to select between
 * them. Gallery mode is always enabled for Essentials mode.
 */
class GalleryMode(rootComponent: JComponent) {

  private val selectionListener: (DataContext, PreviewElementKey?) -> Unit = { dataContext, key ->
    val previewElement = key?.element
    dataContext.findPreviewManager(PreviewModeManager.KEY)?.let { previewManager ->
      previewElement?.let { previewManager.setMode(PreviewMode.Gallery(previewElement)) }
    }
  }

  private val keysProvider: (DataContext) -> Set<PreviewElementKey> = { dataContext ->
    dataContext
      .findPreviewManager(PreviewFlowManager.KEY)
      ?.allPreviewElementsFlow
      ?.value
      ?.asCollection()
      ?.map { element -> PreviewElementKey(element) }
      ?.toSet() ?: emptySet()
  }

  private val selectedProvider: (DataContext) -> PreviewElementKey? = { dataContext ->
    dataContext.findPreviewManager(PreviewModeManager.KEY)?.let { previewManager ->
      (previewManager.mode.value as? PreviewMode.Gallery)?.selected?.let { PreviewElementKey(it) }
    }
  }

  private val galleryView: Gallery<PreviewElementKey> =
    if (StudioFlags.GALLERY_PREVIEW.get())
      GalleryView(rootComponent, selectedProvider, keysProvider, selectionListener)
    else GalleryTabs(rootComponent, selectedProvider, keysProvider, selectionListener)

  /** [JPanel] for [GalleryView]. */
  val component: JComponent = galleryView.component

  /**
   * Simulates a selected [PreviewElementKey] change, firing the [selectionListener]. Intended to be
   * used in tests only.
   */
  @TestOnly
  fun triggerSelectionChange(context: DataContext, previewElement: PreviewElement<*>) {
    selectionListener(context, PreviewElementKey(previewElement))
  }

  @get:TestOnly
  val selectedKey: PreviewElementKey?
    get() = galleryView.selectedKey
}
