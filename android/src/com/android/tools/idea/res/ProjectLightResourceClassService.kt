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

import com.android.utils.concurrency.CacheUtils
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.AndroidManifestUtils
import org.jetbrains.android.facet.AndroidFacet

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of all Android modules in the project.
 */
class ProjectLightResourceClassService(val psiManager: PsiManager, val moduleManager: ModuleManager) : LightResourceClassService {
  companion object {
    private val MODULE_R_CLASS = Key<PsiClass>(ProjectLightResourceClassService::class.qualifiedName!! + "MODULE_R_CLASS")

    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, ProjectLightResourceClassService::class.java)!!
  }

  private val packageCache: Cache<String, PsiPackage> = CacheBuilder.newBuilder().softValues().build()

  override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope): List<PsiClass> {
    val packageName = qualifiedName.dropLast(2)
    return findAllAndroidFacets().mapNotNull { facet ->
      if (AndroidManifestUtils.getPackageName(facet) == packageName && scope.isSearchInModuleContent(facet.module)) {
        val cached = facet.getUserData(MODULE_R_CLASS)
        if (cached?.qualifiedName == qualifiedName) {
          cached
        } else {
          facet.putUserDataIfAbsent(MODULE_R_CLASS, AndroidPackageRClass(psiManager, packageName, facet.module))
        }
      } else {
        null
      }
    }
  }

  override fun findRClassPackage(qualifiedName: String): PsiPackage? {
    return if (findAllAndroidFacets().any { AndroidManifestUtils.getPackageName(it) == qualifiedName }) {
      CacheUtils.getAndUnwrap(packageCache, qualifiedName) { AndroidResourcePackage(psiManager, qualifiedName) }
    } else {
      null
    }
  }

  private fun findAllAndroidFacets(): List<AndroidFacet> {
    // TODO(b/77801019): cache this and figure out how to invalidate that cache.
    return moduleManager.modules.mapNotNull(AndroidFacet::getInstance)
  }
}
