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

import com.android.tools.idea.compose.preview.util.PreviewElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod

/**
 * List of variations of namespaces to be tested by the Compose tests. This is done
 * to support the name migration. We test the old/new preview annotation names with the
 * old/new composable annotation names.
 */
internal val namespaceVariations = listOf(
  arrayOf("androidx.ui.tooling.preview", "androidx.compose"),
  arrayOf("androidx.ui.tooling.preview", "androidx.compose.runtime"),
  arrayOf("androidx.compose.tooling.preview", "androidx.compose"),
  arrayOf("androidx.compose.tooling.preview", "androidx.compose.runtime")
)

internal fun UFile.declaredMethods(): Sequence<UMethod> =
  classes
    .asSequence()
    .flatMap { it.methods.asSequence() }

internal fun UFile.method(name: String): UMethod? =
  declaredMethods()
    .filter { it.name == name }
    .singleOrNull()

internal class StaticPreviewProvider(private val collection: Collection<PreviewElement>): PreviewElementProvider {
  override val previewElements: Sequence<PreviewElement>
    get() = collection.asSequence()
}