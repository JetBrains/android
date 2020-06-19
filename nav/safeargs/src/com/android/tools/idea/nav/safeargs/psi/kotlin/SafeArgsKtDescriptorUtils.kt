/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.tools.idea.nav.safeargs.module.KtDescriptorCacheModuleService
import com.android.tools.idea.nav.safeargs.project.SafeArgsEnabledFacetsProjectComponent
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf

/**
 * Returns the android module associate with the specified package.
 *
 * An android module should usually be found, but as it relies on APIs under the hood which querying indices, and
 * in some cases like we query when dumb mode or primary manifest files are malformed, we might get null.
 */
internal fun findAndroidModuleByPackageName(fqName: FqName, project: Project): Module? {
  val projectSystem = project.getProjectSystem()
  val projectScope = GlobalSearchScope.projectScope(project)
  var packageFqName = fqName

  while(!packageFqName.isRoot) {
    val facet = projectSystem.getAndroidFacetsWithPackageName(project, packageFqName.asString(), projectScope).firstOrNull()
    if (facet != null) return facet.module

    packageFqName = packageFqName.parent()
  }

  return null
}

internal fun getDescriptorsByModule(modulePackageName: FqName, project: Project): Map<FqName, List<PackageFragmentDescriptor>> {
  return project.getComponent(SafeArgsEnabledFacetsProjectComponent::class.java).modulesUsingSafeArgs
    .asSequence()
    .map { it.module }
    .map { module ->
      val candidates = KtDescriptorCacheModuleService.getInstance(module).getDescriptors()
      candidates.filter { it.key.isSubpackageOf(modulePackageName) }
    }
    .fold(mutableMapOf()) { acc, curr ->
      // TODO(b/159954452): duplications(e.g Same fragment class declared across multiple nav resource files) need to be
      //  resolved.
      curr.entries.forEach { entry -> acc.merge(entry.key, entry.value) { old, new -> old + new } }
      acc
    }
}