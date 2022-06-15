/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet

/**
 * Base class for [PsiElementFinder]s that aggregate results from finders chosen by the project system.
 */
abstract class ProjectSystemPsiElementFinder(private val project: Project) : PsiElementFinder() {
  companion object {
    @JvmField val LOG = logger<ProjectSystemPsiElementFinder>()
  }

  protected val finders: Collection<PsiElementFinder>
    get() {
      val projectSystem = project.getProjectSystem()
      return try {
        projectSystem.getPsiElementFinders()
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        // Sometimes we get AbstractMethodError here, see b/109945376.
        LOG.error("Failed to get providers from ${projectSystem::class.qualifiedName}.", e)
        emptyList()
      }
    }
}

/**
 * [ProjectSystemPsiElementFinder] that handles classes, with a higher priority than the default finder.
 */
class ProjectSystemPsiClassFinder(project: Project) : ProjectSystemPsiElementFinder(project) {
  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    for (delegate in finders) {
      return delegate.findClass(qualifiedName, scope) ?: continue
    }

    return null
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    return finders.flatMap { it.findClasses(qualifiedName, scope).asIterable() }.toTypedArray()
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    return finders.flatMap { it.getClasses(psiPackage, scope).asIterable() }.toTypedArray()
  }
}

/**
 * [ProjectSystemPsiElementFinder] that handles packages, with a lower priority than the default finder, meaning custom light packages are
 * only used if there are no corresponding directories in the project.
 */
class ProjectSystemPsiPackageFinder(private val project: Project) : ProjectSystemPsiElementFinder(project) {
  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? = null
  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> = PsiClass.EMPTY_ARRAY

  override fun findPackage(qualifiedName: String): PsiPackage? {
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) return null;

    for (delegate in finders) {
      return delegate.findPackage(qualifiedName) ?: continue
    }

    return null
  }
}
