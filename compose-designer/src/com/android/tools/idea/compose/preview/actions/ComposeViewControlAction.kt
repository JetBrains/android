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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.isPreviewFilterEnabled
import com.android.tools.idea.preview.actions.CommonViewControlAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ComposeViewControlAction() : CommonViewControlAction() {
  init {
    // TODO(b/400717697): remove the filter action or move it into the CommonViewControlAction
    if (ComposeShowFilterAction.shouldBeEnabled()) {
      addSeparator()
      add(ComposeShowFilterAction())
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = !isPreviewFilterEnabled(e.dataContext)
  }
}
