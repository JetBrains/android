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
package com.android.tools.idea.actions

import com.intellij.openapi.actionSystem.DataKey

@JvmField
val LAYOUT_PREVIEW_HANDLER_KEY =
  DataKey.create<LayoutPreviewHandler>(LayoutPreviewHandler::class.java.name)

/**
 * Interface for components that can render and manage previews from a Layout file.
 *
 * TODO(b/356365034) Change [LayoutPreviewHandler] to [StateFlow]
 */
interface LayoutPreviewHandler {
  var previewWithToolsVisibilityAndPosition: Boolean
}
