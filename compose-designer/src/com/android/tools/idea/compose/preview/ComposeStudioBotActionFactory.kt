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
package com.android.tools.idea.compose.preview

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName

interface ComposeStudioBotActionFactory {
  /** An action to generate Compose Previews for the composables in the current file. */
  fun createPreviewGenerator(): AnAction?

  /**
   * An action to transform (e.g. fix, improve, evolve) the selected Compose Preview, taking both
   * the preview image and its corresponding code into account.
   */
  fun transformPreviewAction(): AnAction?

  /**
   * An action to analyze UI images, critique them, and then rewrite the corresponding code to match
   * the target design.
   */
  fun alignUiToTargetImageAction(): AnAction?

  companion object {
    val EP_NAME: ExtensionPointName<ComposeStudioBotActionFactory> =
      ExtensionPointName.create(
        "com.android.tools.idea.compose.preview.composeStudioBotActionFactory"
      )
  }
}
