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

import com.android.tools.compose.code.completion.constraintlayout.InsertionFormat
import com.android.tools.compose.code.completion.constraintlayout.InsertionFormatHandler
import com.android.tools.compose.code.completion.constraintlayout.JsonNewObjectTemplate
import com.android.tools.compose.code.completion.constraintlayout.JsonStringValueTemplate
import com.android.tools.compose.code.completion.constraintlayout.KeyWords
import com.android.tools.compose.code.completion.constraintlayout.provider.model.ConstraintSetsPropertyModel
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonProperty
import com.intellij.util.ProcessingContext

/**
 * Completion provider that looks for the 'ConstraintSets' declaration and passes a model that provides useful functions for inheritors that
 * want to provide completions based con the contents of the 'ConstraintSets' [JsonProperty].
 */
internal abstract class BaseConstraintSetPropertyCompletion : CompletionProvider<CompletionParameters>() {
  final override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val constraintSetsModel = ConstraintSetsPropertyModel.createInstance(parameters)
    if (constraintSetsModel != null) {
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
}

/**
 * Provides options to autocomplete constraint IDs for constraint set declarations, based on the IDs already defined by the user in other
 * constraint sets.
 */
internal object ConstraintSetFieldsProvider : BaseConstraintSetPropertyCompletion() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintSet = constraintSetsPropertyModel.currentConstraintSet
    if (currentConstraintSet != null) {
      constraintSetsPropertyModel.getRemainingFieldsForConstraintSet(currentConstraintSet.name).forEach { fieldName ->
        val template = if (fieldName == KeyWords.Extends) JsonStringValueTemplate else JsonNewObjectTemplate
        result.addLookupElement(name = fieldName, tailText = null, template)
      }
    }
  }
}

/**
 * Autocomplete options with the names of all available ConstraintSets, except from the one the autocomplete was invoked from.
 */
internal object ConstraintSetNamesProvider : BaseConstraintSetPropertyCompletion() {
  override fun addCompletions(
    constraintSetsPropertyModel: ConstraintSetsPropertyModel,
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val currentConstraintSetName = constraintSetsPropertyModel.currentConstraintSet?.name
    val names = constraintSetsPropertyModel.getConstraintSetNames().toMutableSet()
    if (currentConstraintSetName != null) {
      names.remove(currentConstraintSetName)
    }
    names.forEach(result::addLookupElement)
  }
}

private fun CompletionResultSet.addLookupElement(name: String, tailText: String? = null, format: InsertionFormat? = null) {
  var lookupBuilder = if (format == null) {
    LookupElementBuilder.create(name)
  }
  else {
    LookupElementBuilder.create(format, name).withInsertHandler(InsertionFormatHandler)
  }
  lookupBuilder = lookupBuilder.withCaseSensitivity(false)
  if (tailText != null) {
    lookupBuilder = lookupBuilder.withTailText(tailText, true)
  }
  addElement(lookupBuilder)
}