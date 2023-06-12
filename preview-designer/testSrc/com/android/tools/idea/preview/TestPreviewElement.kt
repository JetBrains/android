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
package com.android.tools.idea.preview

internal class TestPreviewElement(
  displayName: String = "",
  groupName: String? = null,
  showDecorations: Boolean = false,
  showBackground: Boolean = false,
  backgroundColor: String? = null,
  displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL,
) : PreviewElement {
  override val displaySettings =
    PreviewDisplaySettings(displayName, groupName, showDecorations, showBackground, backgroundColor, displayPositioning)
  override val previewElementDefinitionPsi = null
  override val previewBodyPsi = null
}