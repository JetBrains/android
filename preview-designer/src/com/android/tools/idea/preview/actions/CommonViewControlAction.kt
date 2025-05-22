/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.actions.ColorBlindModeAction
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.modes.PREVIEW_LAYOUT_OPTIONS
import com.intellij.openapi.actionSystem.KeepPopupOnPerform

/** View control action containing the preview layouts and color-blind mode options. */
open class CommonViewControlAction() :
  ViewControlAction(isEnabled = { !isPreviewRefreshing(it.dataContext) }) {

  init {
    add(
      SwitchSurfaceLayoutManagerAction(
          PREVIEW_LAYOUT_OPTIONS,
          isActionEnabled = {
            !isPreviewRefreshing(it.dataContext) &&
              // If Essentials Mode is enabled, it should not be possible to switch layout.
              !PreviewEssentialsModeManager.isEssentialsModeEnabled
          },
        )
        .apply {
          isPopup = false
          templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
        }
    )
    addSeparator()
    add(ColorBlindModeAction())
  }
}
