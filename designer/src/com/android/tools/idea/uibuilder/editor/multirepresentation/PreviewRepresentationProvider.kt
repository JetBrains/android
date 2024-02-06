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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * A generic interface for a [PreviewRepresentation] provider. Used by the
 * [MultiRepresentationPreview] to find all the [PreviewRepresentation]s suitable for particular
 * file. [PreviewRepresentationProvider] is implied to have 1-to-1 correspondence with some
 * [PreviewRepresentation] that it provides (creates).
 */
interface PreviewRepresentationProvider {
  /** A name associated with the representation */
  val displayName: RepresentationName

  /**
   * Tells a client if the corresponding [PreviewRepresentation] is applicable for the input file.
   */
  suspend fun accept(project: Project, psiFile: PsiFile): Boolean

  /**
   * Creates a corresponding [PreviewRepresentation] for the input file. It is only valid to call
   * this if [accept] is true, undefined behavior otherwise.
   */
  suspend fun createRepresentation(psiFile: PsiFile): PreviewRepresentation
}
