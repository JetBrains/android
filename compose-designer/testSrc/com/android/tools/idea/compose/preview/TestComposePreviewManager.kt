/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.intellij.psi.PsiFile

class TestComposePreviewManager(
  initialInteractiveMode: ComposePreviewManager.InteractiveMode =
    ComposePreviewManager.InteractiveMode.DISABLED
) : ComposePreviewManager {

  var currentStatus =
    ComposePreviewManager.Status(
      hasRuntimeErrors = false,
      hasSyntaxErrors = false,
      isOutOfDate = false,
      areResourcesOutOfDate = false,
      isRefreshing = false,
      interactiveMode = initialInteractiveMode
    )
  var interactiveMode: ComposePreviewManager.InteractiveMode = initialInteractiveMode
    set(value) {
      field = value
      currentStatus = currentStatus.copy(interactiveMode = value)
    }
  override fun status(): ComposePreviewManager.Status = currentStatus

  override val availableGroups: Collection<PreviewGroup> = emptyList()
  override var groupFilter: PreviewGroup = PreviewGroup.ALL_PREVIEW_GROUP
  override var interactivePreviewElementInstance: ComposePreviewElementInstance? = null
    private set
  override var animationInspectionPreviewElementInstance: ComposePreviewElementInstance? = null
  override val hasDesignInfoProviders: Boolean = false
  override val previewedFile: PsiFile? = null
  override suspend fun startInteractivePreview(instance: ComposePreviewElementInstance) {
    interactivePreviewElementInstance = instance
  }

  override fun stopInteractivePreview() {
    interactivePreviewElementInstance = null
  }

  override fun invalidate() {}

  override var isInspectionTooltipEnabled: Boolean = false

  override var isFilterEnabled: Boolean = false

  override fun dispose() {}
}
