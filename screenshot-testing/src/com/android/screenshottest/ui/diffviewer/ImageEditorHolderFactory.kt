/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.ui.diffviewer

import com.intellij.diff.DiffContext
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.tools.holders.EditorHolderFactory

/**
 * Creates ImageEditorHolder for ThreesideDiffViewer.
 */
class ImageEditorHolderFactory : EditorHolderFactory<ImageEditorHolder>() {
  override fun canShowContent(content: DiffContent, context: DiffContext): Boolean = content is ImageDiffContent

  override fun wantShowContent(content: DiffContent, context: DiffContext): Boolean = true

  override fun create(content: DiffContent, context: DiffContext): ImageEditorHolder {
    content as ImageDiffContent
    return ImageEditorHolder(content.bufferedImage)
  }

  companion object {
    val INSTANCE = ImageEditorHolderFactory()
  }
}