/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.apache.commons.lang.time.DurationFormatUtils
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.uast.UElement
import java.time.Duration

fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}

private fun <T : PreviewElement, M> calcAffinityMatrix(
  elements: List<T>, models: List<M>, modelToPreview: (M) -> T?, calcAffinity: (T, T?) -> Int): List<List<Int>> {
  val modelElements = models.map { modelToPreview(it) }
  return elements.map { element -> modelElements.map { calcAffinity(element, it) } }
}

/**
 * Matches [PreviewElement]s with the most similar models. For a [List] of [PreviewElement] ([elements]) returns a [List] of the same
 * size with the indices of the best matched models. The indices are for the input [models] [List]. If there are less [models] than
 * [elements] then indices for some [PreviewElement]s will be set to -1.
 */
internal fun <T : PreviewElement, M> matchElementsToModels(
  models: List<M>, elements: List<T>, modelToPreview: (M) -> T?, calcAffinity: (T, T?) -> Int
): List<Int> {
  val affinityMatrix = calcAffinityMatrix(elements, models, modelToPreview, calcAffinity)
  if (affinityMatrix.isEmpty()) {
    return emptyList()
  }
  val sortedPairs =
    affinityMatrix.first().indices
      .flatMap { modelIdx -> affinityMatrix.indices.map { it to modelIdx } }
      .sortedByDescending { affinityMatrix[it.first][it.second] } // sort in the reverse order to pop from back (quickly)
      .toMutableList()
  val matchedElements = mutableSetOf<Int>()
  val matchedModels = mutableSetOf<Int>()
  val matches = MutableList(affinityMatrix.size) { -1 }

  while (sortedPairs.isNotEmpty()) {
    val (elementIdx, modelIdx) = sortedPairs.pop()
    if (elementIdx in matchedElements || modelIdx in matchedModels) {
      continue
    }
    matches[elementIdx] = modelIdx
    matchedElements.add(elementIdx)
    matchedModels.add(modelIdx)
    // If we either matched all preview elements or all models we have nothing else to do and can finish early
    if (matchedElements.size == affinityMatrix.size || matchedModels.size == affinityMatrix.first().size) {
      break
    }
  }
  return matches
}

fun Segment?.containsOffset(offset: Int) = this?.let {
  it.startOffset <= offset && offset <= it.endOffset
} ?: false

/**
 * Converts the given duration to a display string that contains minutes (if the duration is greater than 60s), seconds and
 * milliseconds.
 */
internal fun Duration.toDisplayString(): String {
  val durationMs = toMillis()
  val durationFormat = if (durationMs >= 60_000) "mm 'm' ss 's' SSS 'ms'" else "ss 's' SSS 'ms'"
  return DurationFormatUtils.formatDuration(durationMs, durationFormat, false)
}
