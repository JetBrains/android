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
package com.android.tools.idea.gradle.dsl.parser.catalog

import com.android.tools.idea.gradle.dsl.api.dependencies.PluginDeclarationSpec
import com.android.tools.idea.gradle.dsl.model.dependencies.PluginDeclarationSpecImpl
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.intellij.openapi.diagnostic.Logger

class FakePluginDeclarationElement(private val parent: GradleDslElement,
                                   name: GradleNameElement,
                                   private val literal: GradleDslLiteral,
                                   private val getter: (PluginDeclarationSpec) -> String?,
                                   private val setter: (PluginDeclarationSpecImpl, String) -> Boolean,
                                   canDelete: Boolean) : FakeElement(parent, name, literal, canDelete) {

  companion object {
    val LOG = Logger.getInstance(FakePluginDeclarationElement::class.java)
  }
  override fun extractValue(): Any? {
    return getSpec()?.let { getter.invoke(it) }
  }

  override fun consumeValue(value: Any?) {
    if (value is String) {
      getSpec()?.let {
        val result = setter.invoke(it, value)
        if (result) {
          literal.setValue(it.compactNotation())
        }
        else {
          LOG.warn("Plugin value was not set: " + value)
        }
      }
    }
  }

  override fun produceRawValue(): Any? {
    return unresolvedValue
  }

  override fun copy(): GradleDslSimpleExpression {
    return FakePluginDeclarationElement(parent, GradleNameElement.copy(myFakeName), literal, getter, setter, myCanDelete)
  }

  override fun getResolvedVariables(): List<GradleReferenceInjection?> = listOf()


  override fun getDependencies(): List<GradleReferenceInjection?> {
    return resolvedVariables
  }

  override fun isReference(): Boolean = false

  private fun getSpec(): PluginDeclarationSpecImpl? {
    val value = literal.getValue(String::class.java) ?: return null
    return PluginDeclarationSpecImpl.create(value)
  }

}