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
package org.jetbrains.android.dom.converters

import com.android.SdkConstants.CLASS_VIEW
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.ResolvingConverter

/**
 * Converter that provides code completion for 'android:autofillHints' attribute.
 *
 * Completion elements are taken from available sources of autofill hint constants:
 * * android.view.View
 * * androidx.autofill.HintConstants (if added to the project)
 */
class AutoFillHintsConverter : ResolvingConverter<String>() {

  companion object {
    private const val AUTOFILL_HINT_PREFIX = "AUTOFILL_HINT"
    private const val ANDROIDX_HINTS_CONSTANTS_CLASS = "androidx.autofill.HintConstants"
  }

  override fun fromString(s: String?, context: ConvertContext) = s

  override fun toString(t: String?, context: ConvertContext) = t

  override fun getVariants(context: ConvertContext): List<String> {
    val project = context.project

    // Instead of saving this on a per module basis, since autoFillHints attribute can have any
    // string value and we are completing the
    // string constants, if any module has androidx.autofill.HintConstants, then any layout will be
    // given all elements.
    return CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result(
        calculateAutoFillHints(project),
        ProjectRootModificationTracker.getInstance(project),
      )
    }
  }

  private fun calculateAutoFillHints(project: Project): List<String> {
    val psiFacade = JavaPsiFacade.getInstance(project)
    val allScope = GlobalSearchScope.allScope(project)
    val viewClass = psiFacade.findClass(CLASS_VIEW, allScope)
    val viewClassConstants =
      viewClass
        ?.allFields
        ?.filter { it.name.contains(AUTOFILL_HINT_PREFIX) }
        ?.mapNotNull { it.computeConstantValue() as? String } ?: return emptyList()

    val hintConstantsClass = psiFacade.findClass(ANDROIDX_HINTS_CONSTANTS_CLASS, allScope)
    val hintConstantsClassConstants =
      hintConstantsClass
        ?.allFields
        ?.filter { it.name.contains(AUTOFILL_HINT_PREFIX) }
        ?.mapNotNull { it.computeConstantValue() as? String } ?: return viewClassConstants

    return viewClassConstants.union(hintConstantsClassConstants).toList()
  }
}
