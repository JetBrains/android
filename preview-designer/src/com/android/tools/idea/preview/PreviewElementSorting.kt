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

import com.android.tools.preview.PreviewElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
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
 * Sorts Previews by descending group name as we want to show ungrouped Previews first. Ungrouped
 * Previews will be grouped as if they have "" as group name.
 */
private val previewGroupsComparator =
  compareByDescending<PreviewElement<*>> { it.displaySettings.group ?: "" }
private val lexicographicalNameComparator = compareBy<PreviewElement<*>> { it.displaySettings.name }

/**
 * Sorts the [PreviewElement]s by [DisplayPositioning] (top first) and then by source code line
 * number, smaller first. Previews belonging to groups will be sorted by their group name, ungrouped
 * Previews will appear at the beginning of the list. When Multipreview is enabled, different
 * Previews may have the same [PreviewElement.previewElementDefinition] value, and those will be
 * ordered lexicographically between them, as the actual Previews may be defined in different files
 * and/or in a not structured way, so it is not possible to order them based on code source offsets.
 */
fun <T : PsiPreviewElement> Collection<T>.sortByDisplayAndSourcePosition(): List<T> =
  runReadAction {
    sortedWith(
      displayPriorityComparator
        .thenComparing(sourceOffsetComparator)
        .thenComparing(previewGroupsComparator)
        .thenComparing(lexicographicalNameComparator)
    )
  }
