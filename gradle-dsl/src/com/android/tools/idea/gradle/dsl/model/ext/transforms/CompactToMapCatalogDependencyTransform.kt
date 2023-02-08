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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile

/**
 * Class transforms compact notation to map notation if user wants to set variable to compact dependency notation
 * that is at catalog file. Toml syntax does not support interpolation.
 */
class CompactToMapCatalogDependencyTransform : PropertyTransform() {
  override fun test(e: GradleDslElement?, holder: GradleDslElement): Boolean {
    if (e == null)
      return false
    return (e is FakeArtifactElement &&
            e.realExpression is GradleDslLiteral &&
            e.realExpression.dslFile is GradleVersionCatalogFile)
  }

  override fun transform(e: GradleDslElement?): GradleDslElement? = e
  override fun bind(holder: GradleDslElement, oldElement: GradleDslElement?, value: Any, name: String): GradleDslExpression? {
    if (oldElement is FakeArtifactElement) {
      val literal = when(value) {
        is ReferenceTo -> GradleVersionCatalogFile.GradleDslVersionLiteral(holder, GradleNameElement.create(name), value)
        else -> oldElement
      }
      literal.setValue(value)
      return literal
    }
    return null
  }

  override fun replace(holder: GradleDslElement,
                       oldElement: GradleDslElement?,
                       newElement: GradleDslExpression,
                       name: String): GradleDslElement {
      val fakeElement = (oldElement as FakeArtifactElement)
      val literal = fakeElement.realExpression as GradleDslLiteral
      val spec = ArtifactDependencySpecImpl.create(literal.value as String)!!
      val expressionMap = GradleDslExpressionMap(holder, GradleNameElement.create(literal.name))
      spec.group?.let {
        expressionMap.setNewLiteral("group", it)
      }
      expressionMap.setNewLiteral("name", spec.name)
      // adding version
      expressionMap.setNewElement(newElement)
      // toml does not support classifier of extension - omit them here
      PropertyUtil.replaceElement(holder, literal, expressionMap)
      return newElement
  }
}