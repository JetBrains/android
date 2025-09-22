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

import com.android.tools.preview.ConfigurablePreviewElement
import com.android.tools.preview.DisplayPositioning
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import java.text.Collator
import java.util.Locale
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Returns the source offset within the file of the [PreviewElement]. We try to read the position of
 * the method but fallback to the position of the annotation if the method body is not valid
 * anymore. If the passed element is null or the position can not be read, this method will return
 * -1.
 *
 * This property needs a [ReadAction] to be read.
 */
private val PsiPreviewElement?.sourceOffset: Int
  get() = this?.previewElementDefinition?.element?.startOffset ?: -1

private val sourceOffsetComparator = compareBy<PsiPreviewElement> { it.sourceOffset }
private val displayPriorityComparator =
  compareBy<PsiPreviewElement> { it.displaySettings.displayPositioning }

/**
 * Returns a [Comparator] that sorts [PreviewElement]s by their display name.
 *
 * This comparator uses [Collator] for locale-sensitive String comparison. This is important for
 * sorting Strings in a way that respects the conventions of a specific language. For example, in
 * some languages, accented characters are sorted differently than in others. Using [Collator] is
 * more robust than a simple [String.compareTo] which just compares Unicode values.
 *
 * @param locale the [Locale] to be used by the [Collator] for comparison.
 */
private fun lexicographicalNameComparator(locale: Locale): Comparator<PreviewElement<*>> =
  compareBy(Collator.getInstance(locale)) { it.displaySettings.name }

/**
 * Gets a common [Locale] for sorting a collection of [PsiPreviewElement]s.
 *
 * Returns a specific [Locale] if exactly one unique locale is defined among all
 * [ConfigurablePreviewElement]s in the collection. Otherwise, returns [Locale.getDefault].
 *
 * @param previewElements The collection of preview elements to analyze.
 * @return The unique [Locale] if one exists, otherwise the system default.
 */
private fun <T : PsiPreviewElement> getPreviewElementLocale(
  previewElements: Collection<T>
): Locale {
  val locales =
    previewElements
      .mapNotNull { (it as? ConfigurablePreviewElement<*>)?.configuration?.locale }
      .toSet()
  return if (locales.size == 1) Locale(locales.first()) else Locale.getDefault()
}

/**
 * Sorts the [PreviewElement]s by [DisplayPositioning] (top first) and then by source code line
 * number, smaller first. When MultiPreview is enabled, different Previews may have the same
 * [PreviewElement.previewElementDefinition] value, and those will be ordered lexicographically
 * between them, as the actual Previews may be defined in different files and/or in a not structured
 * way, so it is not possible to order them based on code source offsets.
 */
fun <T : PsiPreviewElement> Collection<T>.sortByDisplayAndSourcePosition(): List<T> =
  runReadAction {
    sortedWith(
      displayPriorityComparator
        .thenComparing(sourceOffsetComparator)
        .thenComparing(lexicographicalNameComparator(getPreviewElementLocale(this)))
    )
  }
