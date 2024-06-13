/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.mvvm

import com.android.tools.idea.preview.gallery.GalleryMode
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import javax.swing.JComponent

/**
 * Interface that should be implemented by the view exposing the API that is accessed by the
 * [PreviewRepresentation] implementation for updating the previews for the [PreviewElement]s. This
 * is to bypass the [PreviewViewModel] and use the [PreviewView] directly.
 */
interface PreviewRepresentationView {
  /**
   * Returns the [JComponent] containing this [PreviewRepresentationView] that can be used to embed
   * its other panels.
   */
  val component: JComponent

  /**
   * Allows replacing the bottom panel in the [PreviewView]. Used to display the animations
   * component.
   */
  var bottomPanel: JComponent?

  val mainSurface: NlDesignSurface

  /**
   * Set if Gallery Mode is enabled, null if mode is disabled. In Gallery Mode only one preview at a
   * time is rendered. It is always on for Essentials Mode.
   */
  var galleryMode: GalleryMode?
}
