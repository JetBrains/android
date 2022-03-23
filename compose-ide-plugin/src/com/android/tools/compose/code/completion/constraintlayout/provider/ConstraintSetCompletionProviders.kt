/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.code.completion.constraintlayout.provider

import com.android.tools.compose.code.completion.constraintlayout.ConstrainAnchorTemplate
import com.android.tools.compose.code.completion.constraintlayout.ConstraintLayoutKeyWord
import com.android.tools.compose.code.completion.constraintlayout.Dimension
import com.android.tools.compose.code.completion.constraintlayout.JsonNewObjectTemplate
import com.android.tools.compose.code.completion.constraintlayout.JsonNumericValueTemplate
import com.android.tools.compose.code.completion.constraintlayout.JsonStringValueTemplate
import com.android.tools.compose.code.completion.constraintlayout.KeyWords
import com.android.tools.compose.code.completion.constraintlayout.RenderTransform
import com.android.tools.compose.code.completion.constraintlayout.SpecialAnchor
import com.android.tools.compose.code.completion.constraintlayout.StandardAnchor
import com.android.tools.compose.code.completion.constraintlayout.TransitionField
import com.android.tools.compose.code.completion.constraintlayout.provider.model.BaseJsonPropertyModel
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintSetModel
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintSetsPropertyModel
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintsModel
import com.android.tools.compose.completion.addLookupElement
import com.android.tools.compose.completion.inserthandler.InsertionFormat
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import kotlin.reflect.KClass

/**
 * Completion provider that looks for the 'ConstraintSets' declaration and passes a model that provides useful functions for inheritors that
 * want to provide completions based on the contents of the 'ConstraintSets' [JsonProperty].
 */
internal abstract class BaseConstraintSetsCompletionProvider : CompletionProvider<CompletionParameters>() {
  final override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val constraintSetsModel = createConstraintSetsModel(parameters.position)
    if (constraintSetsModel != null) {
      ProgressManager.checkCanceled()
      addCompletions(constraintSetsModel, parameters, result)
    }
  }

  /**
   * Inheritors should implement this function that may pass a reference to the ConstraintSets property.
   */
  abstract fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  )

  /**
   * Finds the [JsonProperty] for the 'ConstraintSets' declaration and returns its model.
   */
  private fun createConstraintSetsModel(initialElement: PsiElement): ConstraintSetsPropertyModel? {
    // The most immediate JsonProperty, including the initialElement if applicable
    val closestProperty = initialElement.parentOfType<JsonProperty>(true) ?: return null
    var current = closestProperty
    var constraintSetsCandidate = current.parentOfType<JsonProperty>(withSelf = false)
    while (constraintSetsCandidate != null && constraintSetsCandidate.name != KeyWords.ConstraintSets) {
      // TODO(b/207030860): Consider creating the model even if there's no property that is explicitly called 'ConstraintSets'
      //    ie: imply that the root JsonObject is the ConstraintSets object, with the downside that figuring out the correct context would
      //    be much more difficult
      current = constraintSetsCandidate
      constraintSetsCandidate = current.parentOfType<JsonProperty>(withSelf = false)
      ProgressManager.checkCanceled()
    }
    return constraintSetsCandidate?.let { ConstraintSetsPropertyModel(it) }
  }
}

/**
 * Provides options to autocomplete constraint IDs for constraint set declarations, based on the IDs already defined by the user in other
 * constraint sets.
 */
internal object ConstraintSetFieldsProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintSet = getJsonPropertyParent(parameters)?.let { ConstraintSetModel(it) } ?: return
    val currentSetName = currentConstraintSet.name ?: return
    constraintSetsPropertyModel.getRemainingFieldsForConstraintSet(currentSetName).forEach { fieldName ->
      val template = if (fieldName == KeyWords.Extends) JsonStringValueTemplate else JsonNewObjectTemplate
      result.addLookupElement(lookupString = fieldName, tailText = null, template)
    }
  }
}

/**
 * Autocomplete options with the names of all available ConstraintSets, except from the one the autocomplete was invoked from.
 */
internal object ConstraintSetNamesProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintSet = getJsonPropertyParent(parameters)?.let { ConstraintSetModel(it) }
    val currentSetName = currentConstraintSet?.name
    val names = constraintSetsPropertyModel.getConstraintSetNames().toMutableSet()
    if (currentSetName != null) {
      names.remove(currentSetName)
    }
    names.forEach(result::addLookupElement)
  }
}

/**
 * Autocomplete options used to define the constraints of a widget (defined by the ID) within a ConstraintSet
 */
internal object ConstraintsProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintsModel = getJsonPropertyParent(parameters)?.let { ConstraintsModel(it) }
    val existingFields = currentConstraintsModel?.declaredFieldNames?.toHashSet() ?: emptySet<String>()
    StandardAnchor.values().forEach {
      if (!existingFields.contains(it.keyWord)) {
        result.addLookupElement(lookupString = it.keyWord, tailText = " [...]", format = ConstrainAnchorTemplate)
      }
    }
    if (!existingFields.contains(KeyWords.Visibility)) {
      result.addLookupElement(lookupString = KeyWords.Visibility, format = JsonStringValueTemplate)
    }
    result.addEnumKeyWordsWithStringValueTemplate<SpecialAnchor>(existingFields)
    result.addEnumKeyWordsWithNumericValueTemplate<Dimension>(existingFields)
    result.addEnumKeyWordsWithNumericValueTemplate<RenderTransform>(existingFields)
  }
}

/**
 * Provides IDs when autocompleting a constraint array.
 *
 * The ID may be either 'parent' or any of the declared IDs in all ConstraintSets, except the ID of the constraints block from which this
 * provider was invoked.
 */
internal object ConstraintIdsProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val possibleIds = constraintSetsPropertyModel.constraintSets.flatMap { it.declaredIds }.toCollection(HashSet())
    // Parent ID should always be present
    possibleIds.add(KeyWords.ParentId)
    // Remove the current ID
    getJsonPropertyParent(parameters)?.name?.let(possibleIds::remove)

    possibleIds.forEach { id ->
      result.addLookupElement(lookupString = id)
    }
  }
}

/**
 * Provides the appropriate anchors when completing a constraint array.
 *
 * [StandardAnchor.verticalAnchors] can only be constrained to other vertical anchors. Same logic for [StandardAnchor.horizontalAnchors].
 */
internal object AnchorablesProvider : BaseConstraintSetsCompletionProvider() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentAnchorKeyWord = parameters.position.parentOfType<JsonProperty>(withSelf = true)?.name ?: return

    val possibleAnchors = when {
      StandardAnchor.isVertical(currentAnchorKeyWord) -> StandardAnchor.verticalAnchors
      StandardAnchor.isHorizontal(currentAnchorKeyWord) -> StandardAnchor.horizontalAnchors
      else -> emptyList()
    }
    possibleAnchors.forEach { result.addLookupElement(lookupString = it.keyWord) }
  }
}

/**
 * Provides completion for the fields of a `Transition`.
 *
 * @see TransitionField
 */
internal object TransitionFieldsProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val transitionPropertyModel = getJsonPropertyParent(parameters)?.let { BaseJsonPropertyModel(it) }
    val existing = transitionPropertyModel?.declaredFieldNames?.toHashSet() ?: emptySet()
    TransitionField.values().forEach {
      if (existing.contains(it.keyWord)) {
        // skip
        return@forEach
      }
      when (it) {
        TransitionField.OnSwipe,
        TransitionField.KeyFrames -> {
          result.addLookupElement(lookupString = it.keyWord, format = JsonNewObjectTemplate)
        }
        else -> {
          result.addLookupElement(lookupString = it.keyWord, format = JsonStringValueTemplate)
        }
      }
    }
  }
}

/**
 * Provides plaint-text completion for each of the elements in the Enum.
 *
 * The provided values come from [ConstraintLayoutKeyWord.keyWord].
 */
internal class EnumValuesCompletionProvider<E>(private val enumClass: KClass<E>)
  : CompletionProvider<CompletionParameters>() where E : Enum<E>, E : ConstraintLayoutKeyWord {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    enumClass.java.enumConstants.forEach {
      result.addLookupElement(lookupString = it.keyWord)
    }
  }
}

/**
 * From the element being invoked, returns the [JsonProperty] parent that also includes the [JsonProperty] from which completion is
 * triggered.
 */
private fun getJsonPropertyParent(parameters: CompletionParameters): JsonProperty? =
  parameters.position.parentOfType<JsonProperty>(withSelf = true)?.parentOfType<JsonProperty>(withSelf = false)

/**
 * Add the [ConstraintLayoutKeyWord.keyWord] of the enum constants as a completion result that takes a string for its value.
 */
private inline fun <reified E> CompletionResultSet.addEnumKeyWordsWithStringValueTemplate(
  existing: Set<String>
) where E : Enum<E>, E : ConstraintLayoutKeyWord {
  addEnumKeywords<E>(result = this, existing = existing, format = JsonStringValueTemplate)
}

/**
 * Add the [ConstraintLayoutKeyWord.keyWord] of the enum constants as a completion result that takes a number for its value.
 */
private inline fun <reified E> CompletionResultSet.addEnumKeyWordsWithNumericValueTemplate(
  existing: Set<String>
) where E : Enum<E>, E : ConstraintLayoutKeyWord {
  addEnumKeywords<E>(result = this, existing = existing, format = JsonNumericValueTemplate)
}

/**
 * Helper function to simplify adding enum constant members to the completion result.
 */
private inline fun <reified E> addEnumKeywords(
  result: CompletionResultSet,
  existing: Set<String> = emptySet(),
  format: InsertionFormat? = null
) where E : Enum<E>, E : ConstraintLayoutKeyWord {
  E::class.java.enumConstants.forEach { constant ->
    if (!existing.contains(constant.keyWord)) {
      result.addLookupElement(lookupString = constant.keyWord, format = format)
    }
  }
}