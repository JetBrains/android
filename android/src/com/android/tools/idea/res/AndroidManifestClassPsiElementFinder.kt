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
import com.android.tools.idea.util.androidFacet
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.android.augment.ManifestClass
import org.jetbrains.android.dom.manifest.AndroidManifestUtils
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

/**
 * [PsiElementFinder] that provides light Manifest classes.
 *
 * This class is a project service, but it's not declared as [PsiElementFinder.EP_NAME] extension. The reason for that is that it's up to
 * the project system to decide whether to use this logic (see [ProjectSystemPsiElementFinder]).
 */
class AndroidManifestClassPsiElementFinder(
  private val psiManager: PsiManager,
  private val projectFacetManager: ProjectFacetManager,
  private val androidLightPackagesCache: AndroidLightPackage.InstanceCache
) : PsiElementFinder() {

  companion object {
    private const val SUFFIX = "." + SdkConstants.FN_MANIFEST_BASE
    private val MODULE_MANIFEST_CLASS = Key<PsiClass>(AndroidManifestClassPsiElementFinder::class.qualifiedName!! + ".MODULE_MANIFEST_CLASS")

    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, AndroidManifestClassPsiElementFinder::class.java)!!

  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope) = findClasses(qualifiedName, scope).firstOrNull()

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
    val targetPackageName = psiPackage.qualifiedName
    return if (targetPackageName.isEmpty()) {
      PsiClass.EMPTY_ARRAY
    }
    else {
      findClasses("$targetPackageName.${SdkConstants.FN_MANIFEST_BASE}", scope)
    }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    if (!qualifiedName.endsWith(SUFFIX)) {
      return PsiClass.EMPTY_ARRAY
    }
    val packageName = qualifiedName.dropLast(SUFFIX.length)

    return findAndroidFacetsWithPackageName(packageName).mapNotNull { facet ->
      getManifestClassForFacet(facet)?.takeIf { PsiSearchScopeUtil.isInScope(scope, it) }
    }.toTypedArray()
  }

  private fun getManifestClassForFacet(facet: AndroidFacet): PsiClass? {
    return if (facet.hasManifestClass()) {
      facet.getUserData(MODULE_MANIFEST_CLASS) ?: facet.putUserDataIfAbsent(MODULE_MANIFEST_CLASS, ManifestClass(facet, psiManager))
    } else {
      null
    }
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    return if (findAndroidFacetsWithPackageName(qualifiedName).isNotEmpty()) {
      androidLightPackagesCache.get(qualifiedName)
    }
    else {
      null
    }
  }

  fun getManifestClassesAccessibleFromModule(module: Module): Collection<PsiClass> {
    val androidFacet = module.androidFacet ?: return emptySet()

    val result = mutableListOf<PsiClass>()
    getManifestClassForFacet(androidFacet)?.let(result::add)
    for (dependency in AndroidUtils.getAllAndroidDependencies(module, false)) {
      getManifestClassForFacet(dependency)?.let(result::add)
    }

    return result
  }

  private fun findAndroidFacetsWithPackageName(packageName: String): List<AndroidFacet> {
    // TODO(b/77801019): cache this and figure out how to invalidate that cache.
    return projectFacetManager.getFacets(AndroidFacet.ID).filter { AndroidManifestUtils.getPackageName(it) == packageName }
  }

  private fun AndroidFacet.hasManifestClass(): Boolean {
    return !AndroidManifestUtils.getCustomPermissions(this).isNullOrEmpty() ||
           !AndroidManifestUtils.getCustomPermissionGroups(this).isNullOrEmpty()
  }
}
