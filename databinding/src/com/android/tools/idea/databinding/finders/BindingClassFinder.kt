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
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope

/**
 * Finder for classes generated from data binding or view binding layout xml files.
 *
 * For example, for a module with an "activity_main.xml" file in it that uses data binding, this
 * class would find the generated "ActivityMainBinding" class.
 *
 * See [LightBindingClass]
 */
class BindingClassFinder(project: Project) : PsiElementFinder() {
  private val dataBindingComponent = project.getComponent(DataBindingProjectComponent::class.java)
  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    return findAllBindingClasses(dataBindingComponent.project)
      .firstOrNull { bindingClass -> qualifiedName == bindingClass.qualifiedName && isInScope(bindingClass, scope) }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    val psiClass = findClass(qualifiedName, scope) ?: return PsiClass.EMPTY_ARRAY
    return arrayOf(psiClass)
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    if (psiPackage.project != scope.project) {
      return PsiClass.EMPTY_ARRAY
    }

    return findAllBindingClasses(psiPackage.project)
      .filter { bindingClass ->
        psiPackage.qualifiedName == bindingClass.qualifiedName.substringBeforeLast('.') && isInScope(bindingClass, scope) }
      .toTypedArray()
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    // data binding packages are found only if corresponding java packages do not exist. For those, we have DataBindingPackageFinder
    // which has a low priority.
    return null
  }

  private fun findAllBindingClasses(project: Project): List<LightBindingClass> {
    return ModuleManager.getInstance(project).modules
      .mapNotNull { module -> module.androidFacet }
      .filter { facet -> facet.isViewBindingEnabled() || DataBindingUtil.isDataBindingEnabled(facet) }
      .mapNotNull { facet -> ResourceRepositoryManager.getModuleResources(facet).dataBindingResourceFiles }
      .flatten() // Set<Group> to Group
      .map { group -> ModuleDataBinding.getInstance(group.mainLayout.psi.facet).getLightBindingClasses(group) }
      .flatten() // List<List<LightBindingClass>> to List<LightBindingClass>
  }

  private fun isInScope(element: PsiElement, scope: GlobalSearchScope) =
    element.containingFile != null && scope.accept(element.containingFile.virtualFile)

}
