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

import com.android.tools.idea.nav.safeargs.project.ProjectNavigationResourceModificationTracker
import com.android.tools.idea.nav.safeargs.project.SafeArgsEnabledFacetsProjectService
import com.android.tools.idea.nav.safeargs.safeArgsModeTracker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.android.facet.AndroidFacet

/** A base class for safe arg light class finder */
abstract class SafeArgsClassFinderBase(private val project: Project) : PsiElementFinder() {

  private fun findAll(project: Project): List<AndroidLightClassBase> {
    val provider = {
      val result =
        SafeArgsEnabledFacetsProjectService.getInstance(project)
          .modulesUsingSafeArgs
          .asSequence()
          .flatMap { facet -> findAll(facet).asSequence() }
          .toList()

      CachedValueProvider.Result.create(
        result,
        ProjectNavigationResourceModificationTracker.getInstance(project),
        project.safeArgsModeTracker
      )
    }

    val manager = CachedValuesManager.getManager(project)
    return manager.getCachedValue(project, manager.getKeyForClass(this.javaClass), provider, false)
  }

  abstract fun findAll(facet: AndroidFacet): List<AndroidLightClassBase>

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    return findAll(project).firstOrNull { lightClass ->
      lightClass.qualifiedName == qualifiedName && PsiSearchScopeUtil.isInScope(scope, lightClass)
    }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    return findAll(project)
      .filter { lightClass ->
        lightClass.qualifiedName == qualifiedName && PsiSearchScopeUtil.isInScope(scope, lightClass)
      }
      .toTypedArray()
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    if (psiPackage.project != scope.project) {
      return PsiClass.EMPTY_ARRAY
    }

    return findAll(psiPackage.project)
      .filter { lightClass ->
        psiPackage.qualifiedName == lightClass.qualifiedName?.substringBeforeLast('.') &&
          PsiSearchScopeUtil.isInScope(scope, lightClass)
      }
      .toTypedArray()
  }
}
