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

import com.android.tools.idea.databinding.LayoutBindingProjectComponent
import com.android.tools.idea.databinding.ModuleDataBinding.Companion.getInstance
import com.android.tools.idea.databinding.cache.ProjectResourceCachedValueProvider
import com.android.tools.idea.databinding.cache.ResourceCacheValueProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet
import java.util.HashMap
import java.util.HashSet

/**
 * A finder responsible for finding data binding packages missing in the app.
 *
 * Note that some packages used by databinding/viewbinding are already found by default finders,
 * and if we try to suggest our own copies, it can confuse the IntelliJ project structure tool
 * window, which thinks there are two packages with the same name.
 *
 * Therefore, this finder is registered with a reduced priority, so it will only suggest packages
 * that were not previously suggested, while data binding class finders are added with a higher
 * priority. See [BindingClassFinder], [DataBindingComponentClassFinder] and
 * [BrClassFinder] for the class-focused finders.
 *
 * See also: https://issuetracker.google.com/37120280
 */
class LayoutBindingPackageFinder(project: Project) : PsiElementFinder() {
  private val component: LayoutBindingProjectComponent = project.getComponent(LayoutBindingProjectComponent::class.java)
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
    packageCache = CachedValuesManager.getManager(project).createCachedValue(
      object : ProjectResourceCachedValueProvider<Map<String, PsiPackage>, Set<String>>(
        component) {
        override fun merge(results: List<Set<String>>): Map<String, PsiPackage> {
          val merged: MutableMap<String, PsiPackage> = HashMap()
          for (result in results) {
            for (qualifiedPackage in result) {
              if (!merged.containsKey(qualifiedPackage)) {
                merged[qualifiedPackage] = component.getOrCreatePsiPackage(qualifiedPackage)
              }
            }
          }
          return merged
        }

        override fun createCacheProvider(facet: AndroidFacet): ResourceCacheValueProvider<Set<String>> {
          return object : ResourceCacheValueProvider<Set<String>>(facet, null) {
            override fun doCompute(): Set<String> {
              val groups = getInstance(getFacet()).bindingLayoutGroups
              if (groups.isEmpty()) {
                return emptySet()
              }
              val result: MutableSet<String> = HashSet()
              for (group in groups) {
                for (layout in group.layouts) {
                  result.add(layout.packageName)
                }
              }
              return result
            }

            override fun defaultValue(): Set<String> {
              return emptySet()
            }
          }
        }
      }, false)
  }
}