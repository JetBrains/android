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

import com.android.SdkConstants
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope

/**
 * Returns true if NavHostFragment is a superclass of the specified class
 */
fun extendsNavHostFragment(psiClass: PsiClass) = psiClass.supers.any { it.qualifiedName == SdkConstants.FQCN_NAV_HOST_FRAGMENT }

/**
 * Returns true if NavHostFragment is either the specified class or a superclass of it
 */
fun isNavHostFragment(className: String, module: Module): Boolean {
  if (className == SdkConstants.FQCN_NAV_HOST_FRAGMENT) {
    return true;
  }

  val javaPsiFacade = JavaPsiFacade.getInstance(module.project) ?: return false
  val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
  val psiClass = javaPsiFacade.findClass(className, scope) ?: return false

  return extendsNavHostFragment(psiClass)
}

fun PsiClass.isInProject() = ModuleUtilCore.findModuleForPsiElement(this) != null
