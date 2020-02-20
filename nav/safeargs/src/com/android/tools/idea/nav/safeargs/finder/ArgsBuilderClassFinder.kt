/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.finder

import com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService
import com.android.tools.idea.nav.safeargs.psi.LightArgsBuilderClass
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import org.jetbrains.android.facet.AndroidFacet

/**
 * A finder that can find instances of [LightArgsBuilderClass] by qualified name / package.
 */
class ArgsBuilderClassFinder(private val project: Project) : PsiElementFinder() {
  companion object {
    fun findAll(project: Project): List<LightArgsBuilderClass> {
      return ModuleManager.getInstance(project).modules
        .mapNotNull { module -> module.androidFacet }
        .flatMap { facet -> findAll(facet) }
    }

    fun findAll(facet: AndroidFacet): List<LightArgsBuilderClass> {
      return SafeArgsCacheModuleService.getInstance(facet).args.map { it.builderClass }
    }
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    return findAll(project)
      .firstOrNull { builderClass ->
        builderClass.qualifiedName == qualifiedName
        && PsiSearchScopeUtil.isInScope(scope, builderClass)
      }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    val psiClass = findClass(qualifiedName, scope) ?: return PsiClass.EMPTY_ARRAY
    return arrayOf(psiClass)
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    if (psiPackage.project != scope.project) {
      return PsiClass.EMPTY_ARRAY
    }

    return findAll(psiPackage.project)
      .filter { builderClass ->
        psiPackage.qualifiedName == builderClass.qualifiedName.substringBeforeLast('.')
        && PsiSearchScopeUtil.isInScope(scope, builderClass)
      }
      .toTypedArray()
  }
}
