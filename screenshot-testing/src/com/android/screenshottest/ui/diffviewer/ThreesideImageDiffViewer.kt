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
import com.intellij.diff.tools.util.side.ThreesideDiffViewer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable

/**
 * Creates a three sided diff viewer using Swing components. Image viewer for each column is defined [ImageEditorHolder]
 */
class ThreesideImageDiffViewer(project: Project, diffContentList: MutableList<DiffContent>) : ThreesideDiffViewer<ImageEditorHolder>(
  ImageDiffContext(project),
  ImageDiffRequest(project, diffContentList),
  ImageEditorHolderFactory.INSTANCE
) {

  override fun performRediff(indicator: ProgressIndicator): Runnable = EmptyRunnable.INSTANCE

  private class ImageDiffContext(private val mProject: Project) : DiffContext() {
    override fun isFocusedInWindow(): Boolean = true

    override fun requestFocusInWindow() = Unit

    override fun getProject(): Project = mProject

    override fun isWindowFocused(): Boolean = false
  }
}