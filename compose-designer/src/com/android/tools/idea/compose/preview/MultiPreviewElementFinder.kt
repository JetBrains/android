/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.uast.UFile

private val filePositionComparator = Comparator.comparingInt<PreviewElement> {
  it?.previewElementDefinitionPsi?.range?.startOffset ?: Int.MAX_VALUE
}

/**
 * A [PreviewElementFinder] that can delegate to other [PreviewElementFinder]s. This will be temporarily used to support multiple
 * implementations of [PreviewElementFinder].
 *
 * This provider will sort the PreviewElements based on their position on the file.
 */
class MultiPreviewElementFinder(private val finders: List<PreviewElementFinder>) : PreviewElementFinder {
  override fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean =
    finders.any { it.hasPreviewMethods(project, vFile) }

  override fun findPreviewMethods(uFile: UFile): List<PreviewElement> =
    finders.flatMap { it.findPreviewMethods(uFile) }
      .distinctBy { it.composableMethodFqn }
      .sortedWith(filePositionComparator)
}