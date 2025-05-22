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

import com.intellij.psi.PsiFile

/**
 * An interface providing [PreviewViewModel] status to the [PreviewView] entities that model can not
 * update directly (e.g. Actions).
 *
 * @property isRefreshing true if the view is currently refreshing.
 * @property hasRenderErrors true if any preview in a file has any render errors that prevent the
 *   preview being up-to-date. For example missing classes.
 * @property hasSyntaxErrors true if the preview is displaying content of a file that has syntax
 *   errors.
 * @property isOutOfDate true if the preview needs a refresh to be up-to-date.
 * @property areResourcesOutOfDate true if the preview needs a build to be up-to-date because
 *   resources are out of date.
 * @property previewedFile the [PsiFile] that this preview is representing, if any. For cases where
 *   the preview is rendering synthetic previews or elements from multiple files, this can be null.
 */
interface PreviewViewModelStatus {
  val isRefreshing: Boolean

  val hasRenderErrors: Boolean

  val hasSyntaxErrors: Boolean

  val isOutOfDate: Boolean

  val areResourcesOutOfDate: Boolean

  val previewedFile: PsiFile?
}
