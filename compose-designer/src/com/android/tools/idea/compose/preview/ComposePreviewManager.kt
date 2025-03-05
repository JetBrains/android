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

import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus

/** Interface that provides access to the Compose Preview logic. */
interface ComposePreviewManager : Disposable, PreviewModeManager, PreviewInvalidationManager {

  /**
   * Status of the preview.
   *
   * @param hasRenderErrors true if the project has any runtime errors that prevent the preview
   *   being up to date. For example missing classes.
   * @param hasSyntaxErrors true if the preview is displaying content of a file that has syntax
   *   errors.
   * @param isOutOfDate true if the preview needs a refresh to be up to date.
   * @param areResourcesOutOfDate true if the preview needs a build to be up to date because
   *   resources are out of date.
   * @param isRefreshing true if the view is currently refreshing.
   * @param psiFilePointer a [SmartPsiElementPointer] to the [PsiFile] that this preview is
   *   representing, if any. For cases where the preview is rendering synthetic previews or elements
   *   from multiple files, this can be null. See [previewedFile] for more information.
   *
   * TODO(b/328056861) replace the use of this data class with PreviewViewModelStatus
   */
  data class Status(
    override val hasRenderErrors: Boolean,
    override val hasSyntaxErrors: Boolean,
    override val isOutOfDate: Boolean,
    override val areResourcesOutOfDate: Boolean,
    override val isRefreshing: Boolean,
    private val psiFilePointer: SmartPsiElementPointer<PsiFile>?,
  ) : PreviewViewModelStatus {
    /** True if the preview has errors that will need a refresh */
    val hasErrors = hasRenderErrors || hasSyntaxErrors

    override val previewedFile: PsiFile?
      get() {
        ThreadingAssertions.assertReadAccess()
        return psiFilePointer?.element
      }
  }

  fun status(): Status

  /** Flag to indicate if the preview filter is enabled or not. */
  var isFilterEnabled: Boolean

  /** Flag to indicate if the UI Check filter is enabled or not. */
  var isUiCheckFilterEnabled: Boolean
}

/**
 * Interface that provides access to the Compose Preview logic that is not stable or meant for
 * public use. This interface contains only temporary or experimental methods.
 */
@ApiStatus.Experimental
interface ComposePreviewManagerEx : ComposePreviewManager {
  /**
   * If enabled, the bounds for the different `@Composable` elements will be displayed in the
   * surface.
   */
  var showDebugBoundaries: Boolean
}
