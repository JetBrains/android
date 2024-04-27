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

import com.android.tools.idea.projectsystem.LightResourceClassService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope

/** Adapter for [LightResourceClassService] to satisfy the [PsiElementFinder] interface. */
class AndroidResourceClassPsiElementFinder(
  private val lightResourceClassService: LightResourceClassService
) : PsiElementFinder() {

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope) =
    findClasses(qualifiedName, scope).firstOrNull()

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    val targetPackageName = psiPackage.qualifiedName
    return if (targetPackageName.isEmpty()) {
      PsiClass.EMPTY_ARRAY
    } else {
      findClasses("$targetPackageName.R", scope)
    }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    if (!qualifiedName.endsWith(".R")) {
      return PsiClass.EMPTY_ARRAY
    }
    val result = lightResourceClassService.getLightRClasses(qualifiedName, scope)
    return result.toTypedArray()
  }

  override fun findPackage(qualifiedName: String): PsiPackage? =
    lightResourceClassService.findRClassPackage(qualifiedName)
}
