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

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.project.LayoutBindingEnabledFacetsProvider
import com.android.tools.idea.databinding.project.ProjectLayoutResourcesModificationTracker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * A finder responsible for finding data binding packages missing in the app.
 *
 * Note that some packages used by databinding/viewbinding are already found by default finders, and
 * if we try to suggest our own copies, it can confuse the IntelliJ project structure tool window,
 * which thinks there are two packages with the same name.
 *
 * Therefore, this finder is registered with a reduced priority, so it will only suggest packages
 * that were not previously suggested, while data binding class finders are added with a higher
 * priority. See [BindingClassFinder], [DataBindingComponentClassFinder] and [BrClassFinder] for the
 * class-focused finders.
 *
 * See also: https://issuetracker.google.com/37120280
 */
class LayoutBindingPackageFinder(project: Project) : PsiElementFinder() {
  private val bindingFacetsProvider = LayoutBindingEnabledFacetsProvider.getInstance(project)
  private val packageFactory = LayoutBindingPackageFactory.getInstance(project)
  private val packageCache: CachedValue<Map<String, PsiPackage>>

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    return null
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    return PsiClass.EMPTY_ARRAY
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    return packageCache.value[qualifiedName]
  }

  init {
    val resourcesModifiedTracker = ProjectLayoutResourcesModificationTracker.getInstance(project)
    packageCache =
      CachedValuesManager.getManager(project).createCachedValue {
        val packages =
          bindingFacetsProvider
            .getAllBindingEnabledFacets()
            .flatMap { facet ->
              val bindingModuleCache = LayoutBindingModuleCache.getInstance(facet)
              val groups = bindingModuleCache.bindingLayoutGroups
              val lightClasses =
                groups.mapNotNull { group ->
                  bindingModuleCache.getLightBindingClasses(group).firstOrNull()
                }
              lightClasses.map { lightClass ->
                val packageName = lightClass.qualifiedName.substringBeforeLast('.')
                packageFactory.getOrCreatePsiPackage(facet, packageName)
              }
            }
            .associateBy { psiPackage -> psiPackage.qualifiedName }

        CachedValueProvider.Result.create(packages, bindingFacetsProvider, resourcesModifiedTracker)
      }
  }
}
