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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.preview.PreviewElementProvider

/**
 * Interface to be implemented by classes providing instances of [ComposePreviewElement] as
 * [ComposePreviewElementInstance].
 */
interface PreviewElementInstanceProvider : PreviewElementProvider<ComposePreviewElementInstance> {
  /** Returns a [Sequence] of [ComposePreviewElementInstance]s. */
  override suspend fun previewElements(): Sequence<ComposePreviewElementInstance>
}

/** [PreviewElementProvider] that does not contain [ComposePreviewElement]s. */
object EmptyPreviewElementInstanceProvider : PreviewElementInstanceProvider {
  override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> = emptySequence()
}

/**
 * Returns the list of available group names in this [PreviewElementProvider]. If this provider does
 * any filtering, the groups returned here will be the ones after the filtering is applied.
 */
fun Sequence<ComposePreviewElement>.groupNames(): Set<String> =
  mapNotNull { it.displaySettings.group }.filter { it.isNotBlank() }.toSet()
