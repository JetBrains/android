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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.psiclass.DataBindingClassFactory
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope

/**
 * Finder for classes generated from data binding layout xml files.
 *
 * For example, for a module with an "activity_main.xml" file in it that uses data binding, this
 * class would find the generated "ActivityMainBinding" class.
 *
 * See [LightBindingClass]
 *
 * TODO(b/129543644): This class cannot change its name or package location until we remove
 *  hardcoded references to it from the Kotlin plugin.
 *  Move back to: finders.LayoutBindingClassFinder
 */
class DataBindingClassFinder(private val dataBindingComponent: DataBindingProjectComponent) : PsiElementFinder() {

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    for (facet in dataBindingComponent.getDataBindingEnabledFacets()) {
      val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
      val info = moduleResources.dataBindingResourceFiles?.get(qualifiedName) ?: continue
      val file = info.psiFile.virtualFile
      if (file != null && scope.accept(file)) {
        return DataBindingClassFactory.getOrCreatePsiClass(info)
      }
    }
    return null
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    val psiClass = findClass(qualifiedName, scope) ?: return PsiClass.EMPTY_ARRAY
    return arrayOf(psiClass)
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    if (psiPackage.project != scope.project) {
      return PsiClass.EMPTY_ARRAY
    }

    return dataBindingComponent.getDataBindingEnabledFacets()
      .flatMap { facet ->
        ResourceRepositoryManager.getModuleResources(facet).dataBindingResourceFiles?.values?.asIterable() ?: emptyList()
      }.filter { info ->
        info.psiFile != null && scope.accept(info.psiFile.virtualFile)
        && psiPackage.qualifiedName == info.packageName
      }.map { info -> DataBindingClassFactory.getOrCreatePsiClass(info) }
      .toTypedArray()
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    // data binding packages are found only if corresponding java packages do not exist. For those, we have DataBindingPackageFinder
    // which has a low priority.
    return null
  }
}
