/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.studio.labs

import org.jetbrains.jewel.ui.icon.IntelliJIconKey

internal object StudioLabsIcons {
  object Features {
    val GenerateComposePreview =
      IntelliJIconKey(
        "images/studio_labs/generate-compose-preview.png",
        "images/studio_labs/generate-compose-preview.png",
        javaClass,
      )
    val PromptLibrarySettings =
      IntelliJIconKey(
        "images/studio_labs/prompt-library-settings.png",
        "images/studio_labs/prompt-library-settings.png",
        javaClass,
      )
    val SuggestedFix =
      IntelliJIconKey(
        "images/studio_labs/suggested-fix.png",
        "images/studio_labs/suggested-fix.png",
        javaClass,
      )
    val TransformComposePreview =
      IntelliJIconKey(
        "images/studio_labs/transform-compose-preview.png",
        "images/studio_labs/transform-compose-preview.png",
        javaClass,
      )
  }
}
