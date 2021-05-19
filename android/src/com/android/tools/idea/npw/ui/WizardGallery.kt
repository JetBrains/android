/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.ui

import com.android.tools.adtui.ASGallery
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE
import com.google.common.base.Function
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Icon

/**
 * The Wizard gallery widget for displaying a collection of images and labels.
 *
 * Relies on two functions to obtain the image and label for the model object.
 */
class WizardGallery<E>(
  title: String, iconProvider: (E?) -> Icon?, labelProvider: (E?) -> String?
) : ASGallery<E>(JBList.createDefaultListModel<Any?>(), Function<E, Icon?> { iconProvider(it) }, Function<E, String?> { labelProvider(it) },
                 DEFAULT_GALLERY_THUMBNAIL_SIZE, null, false) {
  init {
    border = BorderFactory.createLineBorder(JBColor.border()).takeUnless { StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get() }
    getAccessibleContext().accessibleDescription = title
  }

  override fun getPreferredScrollableViewportSize(): Dimension {
    val cellSize = computeCellSize()
    val heightInsets = insets.top + insets.bottom
    val widthInsets = insets.left + insets.right

    // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
    return Dimension(cellSize.width * 5 + widthInsets, (cellSize.height * 2.2).toInt() + heightInsets)
  }
}