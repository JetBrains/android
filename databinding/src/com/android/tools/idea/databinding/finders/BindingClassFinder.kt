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
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.project.ProjectLayoutResourcesModificationTracker
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Finder for classes generated from data binding or view binding layout xml files.
 *
 * For example, for a module with an "activity_main.xml" file in it that uses data binding, this
 * class would find the generated "ActivityMainBinding" class.
 *
 * See [LightBindingClass]
 */
class BindingClassFinder(project: Project) : PsiElementFinder() {
  private val lightBindingsCache: CachedValue<List<LightBindingClass>>
  private val fqcnBindingsCache: CachedValue<Map<String, LightBindingClass>>
  private val packageBindingsCache: CachedValue<Map<String, List<LightBindingClass>>>

  init {
    val component = LayoutBindingProjectComponent.getInstance(project)
    val resourcesModifiedTracker = ProjectLayoutResourcesModificationTracker.getInstance(project)
    val cachedValuesManager = CachedValuesManager.getManager(project)

    lightBindingsCache = cachedValuesManager.createCachedValue {
      val lightBindings = component.getAllBindingEnabledFacets()
        .flatMap { facet ->
          val moduleDataBinding = ModuleDataBinding.getInstance(facet)
          moduleDataBinding.bindingLayoutGroups.flatMap { group -> moduleDataBinding.getLightBindingClasses(group) }
        }
      CachedValueProvider.Result.create(lightBindings, component, resourcesModifiedTracker)
    }

    fqcnBindingsCache = cachedValuesManager.createCachedValue {
      val fqcnBindings = lightBindingsCache.value.associateBy { bindingClass -> bindingClass.qualifiedName }
      CachedValueProvider.Result.create(fqcnBindings, component, resourcesModifiedTracker)
    }

    packageBindingsCache = cachedValuesManager.createCachedValue {
      val packageBindings = lightBindingsCache.value.groupBy { bindingClass -> bindingClass.qualifiedName.substringBeforeLast('.') }
      CachedValueProvider.Result.create(packageBindings, component, resourcesModifiedTracker)
    }
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    return fqcnBindingsCache.value[qualifiedName]
      ?.takeIf { bindingClass -> PsiSearchScopeUtil.isInScope(scope, bindingClass) }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    val psiClass = findClass(qualifiedName, scope) ?: return PsiClass.EMPTY_ARRAY
    return arrayOf(psiClass)
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    val bindingClasses = packageBindingsCache.value[psiPackage.qualifiedName] ?: return PsiClass.EMPTY_ARRAY
    return bindingClasses
      .filter { bindingClass -> PsiSearchScopeUtil.isInScope(scope, bindingClass) }
      .toTypedArray()
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    // data binding packages are found only if corresponding java packages do not exist. For those, we have DataBindingPackageFinder
    // which has a low priority.
    return null
  }
}
