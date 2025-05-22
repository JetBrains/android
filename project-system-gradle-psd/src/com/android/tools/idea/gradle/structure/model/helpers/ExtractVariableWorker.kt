/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.tools.idea.gradle.structure.model.PsVariable
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.maybeValue

class ExtractVariableWorker<PropertyT : Any, out ModelPropertyCoreT : ModelPropertyCore<PropertyT>>(
  private val refactoredProperty: ModelPropertyCoreT
) {
  private val value: Annotated<ParsedValue<PropertyT>>? get() = property?.getParsedValue()

  private var property: ModelPropertyCoreT? = null
  private var variable: PsVariable? = null

  fun changeScope(newScope: PsVariablesScope, currentName: String): Pair<String?, ModelPropertyCoreT> {
    val currentValue: ParsedValue<PropertyT>? = value?.value
    val wasNotRenamed = (property == null) || currentName.isEmpty() || currentName == variable?.name

    this.variable?.delete()
    this.property = null
    this.variable = null

    val preferredName = refactoredProperty.getPreferredVariableName()
    val suggestedName = newScope.getNewVariableName(preferredName)
    val variable = newScope.getOrCreateVariable(suggestedName)
    val property = variable.bindNewPropertyAs(refactoredProperty)!!
    property.setParsedValue(currentValue ?: refactoredProperty.getParsedValue().value)

    this.variable = variable
    this.property = property

    val resultName = if (wasNotRenamed) variable.name else currentName

    return resultName to property
  }

  fun cancel() {
    variable?.delete()
  }

  fun validate(currentName: String): String? {
    return when {
      currentName.isBlank() -> "Variable name is required."
      variable?.value  == ParsedValue.NotSet -> "Cannot bind a variable to an empty value."
      else -> null
    }
  }

  fun commit(currentName: String) {
    variable?.setName(currentName)
    refactoredProperty.setParsedValue(ParsedValue.Set.Parsed(
      dslText = DslText.Reference(currentName),
      value = value!!.value.maybeValue))
  }
}