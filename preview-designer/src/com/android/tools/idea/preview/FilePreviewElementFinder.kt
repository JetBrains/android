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
package com.android.tools.idea.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Interface for the object that can detect and find the [PreviewElement] of a particular type in
 * the file. This interface serves a generic case where [PreviewElement] are nt necessarily marked
 * with @Preview annotations or similar.
 */
interface FilePreviewElementFinder<T : PreviewElement> {
  suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [PreviewElement]s present in the passed [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  suspend fun findPreviewElements(project: Project, vFile: VirtualFile): Collection<T>
}
