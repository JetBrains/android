/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.recompositions

import com.intellij.execution.filters.Filter
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Provides a [Filter] that is applicable only to the recomposition details in the Layout Inspector. A [Filter] is used by
 * EditorHyperlinkSupport to format certain text sequences as hyperlinks.
 */
interface LayoutInspectorRecompositionFilterProvider {

  /** Creates a [Filter] for the text in the [editor]. */
  fun create(editor: EditorEx): Filter

  companion object {
    val EP_NAME: ExtensionPointName<LayoutInspectorRecompositionFilterProvider> =
      ExtensionPointName.create("com.android.tools.idea.layoutinspector.recompositions.filterProvider")
  }
}
