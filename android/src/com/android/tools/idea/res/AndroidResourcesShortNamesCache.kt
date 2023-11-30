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

import com.android.SdkConstants
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor
import org.jetbrains.android.facet.AndroidFacet

/**
 * Superclass for [PsiShortNamesCache] implementations that only provide class names. This is used
 * for code completion of R and Manifest classes, i.e. showing all available R classes after typing
 * "R" in the editor.
 */
sealed class OnlyClassesShortNamesCache(private vararg val classNames: String) :
  PsiShortNamesCache() {
  override fun getAllClassNames() = classNames

  override fun getAllMethodNames() = ArrayUtil.EMPTY_STRING_ARRAY

  override fun getAllFieldNames() = ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFieldsByName(name: String, scope: GlobalSearchScope) = PsiField.EMPTY_ARRAY

  override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int) =
    PsiField.EMPTY_ARRAY

  override fun getMethodsByName(name: String, scope: GlobalSearchScope) = PsiMethod.EMPTY_ARRAY

  override fun getMethodsByNameIfNotMoreThan(
    name: String,
    scope: GlobalSearchScope,
    maxCount: Int
  ) = PsiMethod.EMPTY_ARRAY

  override fun processMethodsWithName(
    name: String,
    scope: GlobalSearchScope,
    processor: Processor<in PsiMethod>
  ) = true
}

/** [PsiShortNamesCache] that provides names of known light R classes. */
class AndroidResourcesShortNamesCache(private val project: Project) :
  OnlyClassesShortNamesCache(SdkConstants.R_CLASS) {

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    if (name != SdkConstants.R_CLASS) return PsiClass.EMPTY_ARRAY

    return project
      .getProjectSystem()
      .getLightResourceClassService()
      .allLightRClasses
      .filter { PsiSearchScopeUtil.isInScope(scope, it) }
      .toTypedArray()
  }
}

/** [PsiShortNamesCache] that provides names of known light Manifest classes. */
class AndroidManifestShortNamesCache(private val project: Project) :
  OnlyClassesShortNamesCache(SdkConstants.FN_MANIFEST_BASE) {
  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    if (name != SdkConstants.FN_MANIFEST_BASE) return PsiClass.EMPTY_ARRAY

    val finder = AndroidManifestClassPsiElementFinder.getInstance(project)
    return ProjectFacetManager.getInstance(project)
      .getFacets(AndroidFacet.ID)
      .mapNotNull { finder.getManifestClassForFacet(it) }
      .toTypedArray()
  }
}
