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

import com.android.tools.idea.preview.modes.PreviewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

open class TestComposePreviewManager : ComposePreviewManager {

  var currentStatus =
    ComposePreviewManager.Status(
      hasErrorsAndNeedsBuild = false,
      hasSyntaxErrors = false,
      isOutOfDate = false,
      areResourcesOutOfDate = false,
      isRefreshing = false,
      psiFilePointer = null,
    )

  override fun status(): ComposePreviewManager.Status = currentStatus

  override fun invalidate() {}

  override var isFilterEnabled: Boolean = false

  override var isUiCheckFilterEnabled: Boolean = false

  private val _mode: MutableStateFlow<PreviewMode> = MutableStateFlow(PreviewMode.Default())
  override val mode = _mode.asStateFlow()

  override fun restorePrevious() {}

  override fun setMode(mode: PreviewMode) {
    _mode.value = mode
  }

  override fun dispose() {}
}
