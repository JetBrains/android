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

import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.intellij.psi.PsiFile

class TestComposePreviewManager(var interactiveMode: ComposePreviewManager.InteractiveMode = ComposePreviewManager.InteractiveMode.DISABLED) : ComposePreviewManager {

  override fun status(): ComposePreviewManager.Status =
    ComposePreviewManager.Status(hasRuntimeErrors = false,
                                 hasSyntaxErrors = false,
                                 isOutOfDate = false,
                                 isRefreshing = false,
                                 interactiveMode = interactiveMode)

  override var isBuildOnSaveEnabled: Boolean = false
  override val availableGroups: Collection<PreviewGroup> = emptyList()
  override var groupFilter: PreviewGroup = PreviewGroup.ALL_PREVIEW_GROUP
  override var interactivePreviewElementInstance: PreviewElementInstance? = null
    private set
  override var animationInspectionPreviewElementInstance: PreviewElementInstance? = null
  override val hasLiveLiterals: Boolean = false
  override val isLiveLiteralsEnabled: Boolean = false
  override val hasDesignInfoProviders: Boolean = false
  override val previewedFile: PsiFile? = null
  override suspend fun startInteractivePreview(instance: PreviewElementInstance) {
    interactivePreviewElementInstance = instance
  }

  override fun stopInteractivePreview() {
    interactivePreviewElementInstance = null
  }

  override fun dispose() {}
}