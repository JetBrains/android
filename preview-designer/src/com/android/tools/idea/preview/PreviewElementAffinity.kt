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
package com.android.tools.idea.preview

import com.android.tools.preview.MethodPreviewElement
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlin.collections.removeLastOrNull

private sealed class PreviewElementMatcher<K>(val keySelector: (PreviewElement<*>?) -> K?) {
  fun groupAndMatch(
    indexedElements: MutableSet<Pair<Int, PreviewElement<*>>>,
    indexedModelElements: MutableSet<Pair<Int, PreviewElement<*>?>>,
    modelIndexForElement: MutableList<Int>,
  ) {
    val groups = indexedModelElements.groupByTo(mutableMapOf()) { keySelector(it.second) }
    indexedElements.forEach { (elementIdx, element) ->
      if (modelIndexForElement[elementIdx] == -1) {
        val (modelIdx, modelElement) =
          groups[keySelector(element)]?.removeLastOrNull() ?: return@forEach
        modelIndexForElement[elementIdx] = modelIdx
        indexedModelElements.remove(modelIdx to modelElement)
      }
    }
  }

  fun match(el1: PreviewElement<*>, el2: PreviewElement<*>?) = keySelector(el1) == keySelector(el2)
}

private object Identity : PreviewElementMatcher<PreviewElement<*>>({ it })

private object MethodAndSettings :
  PreviewElementMatcher<Pair<String?, PreviewDisplaySettings?>>({
    (it as? MethodPreviewElement<*>)?.methodFqn to it?.displaySettings
  })

private object Method :
  PreviewElementMatcher<String?>({ (it as? MethodPreviewElement<*>)?.methodFqn })

private object None : PreviewElementMatcher<Unit>({})

private val matchersSortedByPriority = listOf(Identity, MethodAndSettings, Method, None)

/**
 * Returns a number indicating how [el1] [PreviewElement] is to the [el2] [PreviewElement]. 0
 * meaning they are equal and higher the number the more dissimilar they are. This allows for, when
 * re-using models, the model with the most similar [PreviewElement] is re-used. When the user is
 * just switching groups or selecting a specific model, this allows switching to the existing
 * preview faster.
 */
fun <T : PreviewElement<*>> calcAffinity(el1: T, el2: T?): Int =
  matchersSortedByPriority
    .indexOfFirst { it.match(el1, el2) }
    .also {
      // Any pair of elements should match with the None key, so the result should never be -1
      assert(it != -1)
    }

/**
 * Matches [PreviewElement]s with the most similar models. For a [List] of [PreviewElement]
 * ([elements]) returns a [List] of the same size with the indices of the best matched models. The
 * indices are for the input [models] [List]. If there are less [models] than [elements] then
 * indices for some [PreviewElement]s will be set to -1.
 */
@RequiresBackgroundThread
fun <T : PreviewElement<*>, M> matchElementsToModels(
  models: List<M>,
  elements: List<T>,
  previewElementModelAdapter: PreviewElementModelAdapter<T, M>,
): List<Int> {
  val indexedElements: MutableSet<Pair<Int, PreviewElement<*>>> =
    elements.mapIndexed { elementIdx, element -> elementIdx to element }.toMutableSet()

  val indexedModelElements: MutableSet<Pair<Int, PreviewElement<*>?>> =
    models
      .mapIndexed { modelIdx, model ->
        modelIdx to previewElementModelAdapter.modelToElement(model)
      }
      .toMutableSet()

  val modelIndexForElement = MutableList(elements.size) { -1 }
  matchersSortedByPriority.forEach {
    it.groupAndMatch(indexedElements, indexedModelElements, modelIndexForElement)
  }
  return modelIndexForElement
}
