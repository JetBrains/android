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
package com.android.tools.idea.sqlite.settings

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiModifier.ABSTRACT
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ClassInheritorsSearch

/**
 * A [CompletionContributor] for [ClassPicker]
 *
 * Based on `AndroidActivityAliasCompletionContributor`
 */
class ClassPickerCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    if (parameters.completionType != CompletionType.BASIC) {
      return
    }
    val prefix = parameters.editor.document.text.substring(0, parameters.offset)
    val result = result.withPrefixMatcher(prefix)
    val editor = parameters.editor
    val project = editor.project ?: return
    val base = editor.getUserData(ClassPicker.BASE_CLASS_KEY) ?: return
    val facade = JavaPsiFacade.getInstance(project)
    val scope = ProjectScope.getAllScope(project)

    val baseClass = facade.findClass(base, scope) ?: return

    // TODO(aalbert): Filter classes we should not track like BundledSQLiteDriver etc
    ClassInheritorsSearch.search(
        /* aClass = */ baseClass,
        /* scope = */ scope,
        /* checkDeep = */ true,
        /* checkInheritance = */ true,
        /* includeAnonymous = */ false,
      )
      .filterNotNull()
      .forEach {
        val modifiers = it.modifierList ?: return@forEach
        if (modifiers.hasModifierProperty(ABSTRACT)) {
          return@forEach
        }
        val name = it.qualifiedName ?: return@forEach
        result.addElement(JavaLookupElementBuilder.forClass(it, name))
      }

    result.stopHere()
  }
}
