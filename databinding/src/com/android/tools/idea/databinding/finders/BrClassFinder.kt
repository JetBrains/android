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
package com.android.tools.idea.databinding.finders

import com.android.tools.idea.databinding.DataBindingProjectComponent
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.databinding.psiclass.DataBindingClassFactory
import com.android.tools.idea.databinding.psiclass.LightBrClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * A finder responsible for finding all the BR files in this project.
 *
 * See [LightBrClass]
 */
class BrClassFinder(project: Project) : PsiElementFinder() {
  private val classByPackageCache: CachedValue<Map<String, PsiClass>>

  init {
    classByPackageCache = CachedValuesManager.getManager(project).createCachedValue(
      {
        val component = project.getComponent(DataBindingProjectComponent::class.java)
        val classes = component.getDataBindingEnabledFacets()
          .associate { facet ->
            DataBindingUtil.getBrQualifiedName(facet) to DataBindingClassFactory.getOrCreateBrClassFor(facet)
          }

        CachedValueProvider.Result.create<Map<String, PsiClass>>(classes, component)
      }, false)
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    if (psiPackage.project != scope.project) {
      return PsiClass.EMPTY_ARRAY
    }

    val qualifiedPackage = psiPackage.qualifiedName
    return if (qualifiedPackage.isNotEmpty()) {
      findClasses("$qualifiedPackage.${DataBindingUtil.BR}", scope)
    }
    else {
      PsiClass.EMPTY_ARRAY
    }
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    if (!qualifiedName.endsWith(DataBindingUtil.BR)) {
      return null
    }
    val psiClass = classByPackageCache.value[qualifiedName]
    return psiClass?.takeIf { psiClass.project == scope.project }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    val aClass = findClass(qualifiedName, scope) ?: return PsiClass.EMPTY_ARRAY
    return arrayOf(aClass)
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    return null
  }
}
