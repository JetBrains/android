/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.cache

import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.LayoutBindingProjectComponent
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.psiclass.LightBrClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor
import com.intellij.util.containers.HashSet

private val BR_CLASS_NAME_LIST = arrayOf(DataBindingUtil.BR)

/**
 * Cache that stores the BR instances associated with each module.
 *
 * See [LightBrClass]
 */
class BrShortNamesCache(project: Project) : PsiShortNamesCache() {
  private val component = project.getComponent(LayoutBindingProjectComponent::class.java)
  private val allFieldNamesCache: CachedValue<Array<String>>

  init {
    allFieldNamesCache = CachedValuesManager.getManager(component.project).createCachedValue(
      {
        val facets = component.getDataBindingEnabledFacets()
        val allFields = facets
          .mapNotNull { facet -> ModuleDataBinding.getInstance(facet).lightBrClass }
          .flatMap { brClass -> brClass.allFieldNames.asIterable() }
          .toTypedArray()

        CachedValueProvider.Result.create(allFields, component)
      }, false)
  }

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    if (!isMyScope(scope) || DataBindingUtil.BR != name) {
      return PsiClass.EMPTY_ARRAY
    }

    return component.getDataBindingEnabledFacets()
      .filter { facet -> scope.isSearchInModuleContent(facet.module) }
      .mapNotNull { facet -> ModuleDataBinding.getInstance(facet).lightBrClass }
      .toTypedArray()
  }

  override fun getAllClassNames(): Array<String> {
    return BR_CLASS_NAME_LIST
  }

  override fun getAllClassNames(dest: HashSet<String>) {
    dest.add(DataBindingUtil.BR)
  }

  override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
    // BR files are only fields, no methods
    return PsiMethod.EMPTY_ARRAY
  }

  override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> {
    // BR files are only fields, no methods
    return PsiMethod.EMPTY_ARRAY
  }

  override fun processMethodsWithName(name: String,
                                      scope: GlobalSearchScope,
                                      processor: Processor<PsiMethod>): Boolean {
    // BR files are only fields, no methods
    return true
  }

  override fun getAllMethodNames(): Array<String> {
    // BR files are only fields, no methods
    return ArrayUtil.EMPTY_STRING_ARRAY
  }

  override fun getAllMethodNames(set: HashSet<String>) {
    // BR files are only fields, no methods
  }

  override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
    if (!isMyScope(scope)) {
      return PsiField.EMPTY_ARRAY
    }

    return getClassesByName(DataBindingUtil.BR, scope)
      .mapNotNull { psiClass -> psiClass.findFieldByName(name, false) }
      .toTypedArray()
  }

  override fun getAllFieldNames(): Array<String> {
    return allFieldNamesCache.value
  }

  override fun getAllFieldNames(set: HashSet<String>) {
    set.addAll(allFieldNames)
  }

  override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }

  private fun isMyScope(scope: GlobalSearchScope): Boolean {
    return (component.project == scope.project)
  }
}
