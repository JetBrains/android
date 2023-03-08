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
package org.jetbrains.android.dom.navigation

import com.android.SdkConstants.FQCN_NAV_HOST_FRAGMENT
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.psi.PsiModifier

/**
 * Returns true if NavHostFragment is a superclass of the specified class
 */
fun extendsNavHostFragment(psiClass: PsiClass, module: Module): Boolean {
  val navHostClass = getClass(FQCN_NAV_HOST_FRAGMENT, module) ?: return false
  return psiClass.isInheritor(navHostClass, true)
}

/**
 * Returns true if NavHostFragment is either the specified class or a superclass of it
 */
fun isNavHostFragment(className: String, module: Module): Boolean {
  if (className == FQCN_NAV_HOST_FRAGMENT) {
    return true
  }

  val psiClass = getClass(className, module) ?: return false
  return extendsNavHostFragment(psiClass, module)
}

private fun getClass(className: String, module: Module): PsiClass? {
  val javaPsiFacade = JavaPsiFacade.getInstance(module.project) ?: return null
  val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
  return javaPsiFacade.findClass(className, scope)
}

fun PsiClass.isInProject() = ModuleUtilCore.findModuleForPsiElement(this) != null

fun getClassesForTag(module: Module, tag: String): Map<PsiClass, String?> {
  val result = mutableMapOf<PsiClass, String?>()
  val schema = NavigationSchema.get(module)

  for (dynamicModule in dynamicModules(module)) {
    val scope = GlobalSearchScope.moduleWithDependenciesScope(dynamicModule)
    schema.getProjectClassesForTag(tag, scope)
      .associateWithTo(result) { dynamicModule.name }
  }

  schema.getProjectClassesForTag(tag).associateWithTo(result) { null }

  return result.filterKeys { it.modifierList?.hasModifierProperty(PsiModifier.ABSTRACT) != true }
}

fun dynamicModules(module: Module): List<Module> {
  return module.getModuleSystem().getDynamicFeatureModules()
}

