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

import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Default [ComposeFilePreviewElementFinder]. This will be used by default by production code */
val defaultFilePreviewElementFinder = AnnotationFilePreviewElementFinder

/**
 * Interface to be implemented by classes able to find [PsiComposePreviewElement]s on
 * [VirtualFile]s.
 */
interface ComposeFilePreviewElementFinder : FilePreviewElementFinder<PsiComposePreviewElement> {
  /**
   * Returns if this file contains `@Composable` methods. This is similar to [hasPreviewElements]
   * but allows deciding if this file might allow previews to be added.
   */
  suspend fun hasComposableMethods(project: Project, vFile: VirtualFile): Boolean
}
