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
package com.android.tools.compose.code.completion.constraintlayout.provider.model

import com.android.tools.compose.code.completion.constraintlayout.KeyWords
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * Model for the `ConstraintSets` Json block.
 *
 * The `ConstraintSets` Json block, is a collection of different ConstraintSets, each of which describes a state of the layout by defining
 * properties of each of its widgets such as width, height or their layout constraints.
 *
 * @param constraintSetsElement The PSI element of the `ConstraintSets` Json property
 */
internal class ConstraintSetsPropertyModel private constructor(constraintSetsElement: JsonProperty, currentConstraintSet: JsonProperty) {
  private val constraintSetsPointer = SmartPointerManager.createPointer(constraintSetsElement)
  private val currentSetPointer = SmartPointerManager.createPointer(currentConstraintSet)

  /**
   * The [JsonProperty] for the ConstraintSet being edited
   */
  val currentConstraintSet: JsonProperty?
    get() = currentSetPointer.element

  /**
   * List of all ConstraintSet elements in the Json block.
   */
  val constraintSets: List<JsonProperty>
    get() = constraintSetsPointer.element.getInnerProperties()

  /**
   * The names of all ConstraintSets in this block.
   */
  fun getConstraintSetNames(): List<String> {
    return constraintSets.map { it.name }
  }

  /**
   * Returns the remaining possible fields for the given [constraintSetName], this is done by reading all fields in all ConstraintSets and
   * subtracting the fields already present in [constraintSetName]. Most of these should be the IDs that represent constrained widgets.
   */
  fun getRemainingFieldsForConstraintSet(constraintSetName: String): List<String> {
    val availableNames = mutableSetOf(KeyWords.Extends)
    val usedNames = mutableSetOf<String>()
    constraintSets.forEach { constraintSet ->
      constraintSet.getInnerProperties().map { it.name }.forEach { propertyName ->
        if (constraintSet.name == constraintSetName) {
          usedNames.add(propertyName)
        }
        else {
          availableNames.add(propertyName)
        }
      }
    }
    availableNames.removeAll(usedNames)
    return availableNames.toList()
  }

  companion object {
    /**
     * Creates the model if the [JsonProperty] corresponding to the ConstraintSets Json block is found.
     */
    fun createInstance(parameters: CompletionParameters): ConstraintSetsPropertyModel? {
      // TODO(b/207030860): Cache the instance of the model, and figure out a robust way to cache elements of the model instead of reading
      //  them every time, see CachedValuesManager
      val closestProperty = parameters.position.parentOfType<JsonProperty>(true)
      val modelParameters = closestProperty?.let { findModelParameters(it) }

      return modelParameters?.let {
        ConstraintSetsPropertyModel(
          constraintSetsElement = modelParameters.constraintSetsElement,
          currentConstraintSet = modelParameters.currentConstraintSet
        )
      }
    }

    /**
     * Attempts to find the required elements needed for the model. The search is done by traversing the elements towards the root.
     */
    private fun findModelParameters(current: JsonProperty): ConstraintSetsModelData? {
      val parent = current.parentOfType<JsonProperty>(false)
      if (parent != null) {
        if (current == parent) {
          return null
        }
        if (parent.name == KeyWords.ConstraintSets) {
          // TODO(b/207030860): Consider creating the model even if there's no property that is explicitly called 'ConstraintSets'
          //    ie: imply that the root JsonObject is the ConstraintSets object, this might prompt the autocomplete in the incorrect context
          //    but will guarantee that it always works for ConstraintLayout
          return ConstraintSetsModelData(
            constraintSetsElement = parent,
            currentConstraintSet = current
          )
        }
        else {
          return findModelParameters(parent)
        }
      }
      return null
    }

    /**
     * Helper class that contains the parameters needed for the model.
     *
     * @param constraintSetsElement The element of the block that contains all ConstraintSets
     * @param currentConstraintSet The element of the current ConstraintSet being edited, should always be present
     */
    private data class ConstraintSetsModelData(
      // TODO(b/207030860): The JsonProperty for the block that describes a widget's constraints could also be useful (if applicable)
      val constraintSetsElement: JsonProperty,
      val currentConstraintSet: JsonProperty
    )
  }
}

private fun JsonProperty?.getInnerProperties(): List<JsonProperty> =
  this?.getChildOfType<JsonObject>()?.propertyList?.toList() ?: emptyList()
