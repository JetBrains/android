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
package com.android.tools.idea.compose.preview.lite

import com.android.tools.idea.editors.mode.PreviewEssentialsModeManager
import com.android.tools.idea.flags.StudioFlags
import org.jetbrains.android.uipreview.AndroidEditorSettings

/**
 * Service to handle and query the state of Compose Preview Lite Mode. The state can be set via
 * settings.
 */
object ComposePreviewLiteModeManager {

  val isLiteModeEnabled: Boolean
    get() =
      StudioFlags.COMPOSE_PREVIEW_LITE_MODE.get() &&
        (AndroidEditorSettings.getInstance().globalState.isComposePreviewLiteModeEnabled ||
          PreviewEssentialsModeManager.isInEssentialsMode)
}
