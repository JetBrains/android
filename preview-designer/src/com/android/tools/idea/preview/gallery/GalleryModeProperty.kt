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

import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.reflect.KProperty

/**
 * [GalleryMode] property delegate to be used by views which need [GalleryMode] support.
 *
 * When the [GalleryMode] is not null, this property delegate replaces the given [mainSurface] from
 * the given [component] with a [JPanel] containing tabs provided by [GalleryMode.component] at the
 * north and [mainSurface] in the center.
 *
 * When the [GalleryMode] is null, the [mainSurface] is restored within [content] and the [JPanel]
 * with the gallery tabs is removed.
 */
class GalleryModeProperty(private val content: JPanel, private val mainSurface: NlDesignSurface) :
  JPanel(BorderLayout()) {

  private var galleryMode: GalleryMode? = null

  operator fun getValue(thisRef: Any?, property: KProperty<*>) = galleryMode

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: GalleryMode?) {
    // Avoid repeated values.
    if (value == galleryMode) return
    // If essentials mode is enabled, disabled or updated - components should be rearranged.
    // Remove components from its existing places.
    if (galleryMode == null) {
      content.remove(mainSurface)
    } else {
      removeAll()
      content.remove(this)
    }

    // Add components to new places.
    if (value == null) {
      content.add(mainSurface, BorderLayout.CENTER)
    } else {
      add(value.component, BorderLayout.NORTH)
      add(mainSurface, BorderLayout.CENTER)
      content.add(this)
    }
    galleryMode = value
  }
}
