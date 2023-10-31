/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext.transforms

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap

class MapPropertyTransform(val propertyName:String): PropertyTransform() {
  override fun test(e: GradleDslElement?, holder: GradleDslElement): Boolean = e is GradleDslExpressionMap

  override fun transform(e: GradleDslElement?): GradleDslElement? =
    (e as? GradleDslExpressionMap)?.getElement(propertyName)

  override fun bind(holder: GradleDslElement, oldElement: GradleDslElement?, value: Any, name: String): GradleDslExpression? =
    (oldElement as? GradleDslExpressionMap)?.setNewLiteral(propertyName, value)


  override fun replace(holder: GradleDslElement,
                       oldElement: GradleDslElement?,
                       newElement: GradleDslExpression,
                       name: String): GradleDslElement? {
    val map = oldElement as? GradleDslExpressionMap ?: return null
    map.setNewElement(newElement)
    return newElement
  }
}