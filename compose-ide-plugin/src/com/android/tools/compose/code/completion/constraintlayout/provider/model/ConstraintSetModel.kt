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
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral

/**
 * Model for the JSON block corresponding to a single ConstraintSet.
 *
 * A ConstraintSet is a state that defines a specific layout of the contents in a ConstraintLayout.
 */
internal class ConstraintSetModel(jsonProperty: JsonProperty) : BaseJsonPropertyModel(jsonProperty) {
  /**
   * Name of the ConstraintSet.
   */
  val name: String? = elementPointer.element?.name

  /**
   * Name of the ConstraintSet this is extending constraints from.
   */
  val extendsFrom: String? = (findProperty(KeyWords.Extends)?.value as? JsonStringLiteral)?.value

  /**
   * The constraints (by widget ID) explicitly declared in this ConstraintSet.
   *
   * Note that it does not resolve constraints inherited from [extendsFrom].
   */
  val constraintsById: Map<String, ConstraintsModel> =
    innerProperties.associate { property ->
      property.name to ConstraintsModel(property)
    }

  // TODO(b/207030860): Add a method that can pull all resolved constraints for each widget ID, it could be useful to make sure we are not
  //  offering options that are implicitly present from the 'Extends' ConstraintSet
}