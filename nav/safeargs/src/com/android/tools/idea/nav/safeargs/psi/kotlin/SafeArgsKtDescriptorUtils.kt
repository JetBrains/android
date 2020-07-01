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
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.idea.core.unwrapModuleSourceInfo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm

/**
 * Returns the android module associate with the specified package within the context that either it's the dependency
 * or it's the module itself.
 *
 * An android module should usually be found, but as it relies on APIs under the hood which querying indices, and
 * in some cases like we query when dumb mode or primary manifest files are malformed, we might get null.
 */
internal fun findAndroidModuleByPackageName(fqName: FqName, project: Project, moduleContext: Module): Module? {
  val projectSystem = project.getProjectSystem()
  val projectScope = GlobalSearchScope.projectScope(project)
  var packageFqName = fqName

  while (!packageFqName.isRoot) {
    val facets = projectSystem.getAndroidFacetsWithPackageName(project, packageFqName.asString(), projectScope)

    if (facets.isNotEmpty()) {
      return facets.firstOrNull {
        moduleContext == it.module || ModuleRootManager.getInstance(moduleContext).isDependsOn(it.module)
      }?.module
    }

    packageFqName = packageFqName.parent()
  }

  return null
}

internal fun getDescriptorsByModule(module: Module, project: Project): Map<FqName, List<PackageFragmentDescriptor>> {
  return project.getComponent(SafeArgsEnabledFacetsProjectComponent::class.java).modulesUsingSafeArgs
    .asSequence()
    .map { it.module }
    .filter { it == module || ModuleRootManager.getInstance(it).isDependsOn(module) }
    .flatMap {
      KtDescriptorCacheModuleService.getInstance(it).getDescriptors().entries.asSequence()
    }
    .filter { (_, descriptors) ->
      descriptors.first().containingDeclaration.toModule().let { it == module }
    }
    .fold(mutableMapOf()) { acc, curr ->
      // TODO(b/159954452): duplications(e.g Same fragment class declared across multiple nav resource files) need to be
      //  resolved.
      acc.merge(curr.key, curr.value) { old, new -> old + new }
      acc
    }
}

internal fun getDescriptorsByModulesWithDependencies(module: Module): Map<FqName, List<PackageFragmentDescriptor>> {
  val project = module.project
  return project.getComponent(SafeArgsEnabledFacetsProjectComponent::class.java)
    .modulesUsingSafeArgs
    .asSequence()
    .map { it.module }
    .map { ProgressManager.checkCanceled(); it }
    .flatMap { KtDescriptorCacheModuleService.getInstance(it).getDescriptors().entries.asSequence() }
    .filter { (_, descriptors) ->
      descriptors.first().containingDeclaration.toModule()
        ?.let { it == module || ModuleRootManager.getInstance(module).isDependsOn(it) } == true
    }
    .fold(mutableMapOf()) { acc, curr ->
      // TODO(b/159954452): duplications(e.g Same fragment class declared across multiple nav resource files) need to be
      //  resolved.
      acc.merge(curr.key, curr.value) { old, new -> old + new }
      acc
    }
}

internal fun ModuleInfo.toModule(): Module? {
  return this.unwrapModuleSourceInfo()?.takeIf { it.platform.isJvm() }?.module
}

internal fun ModuleDescriptor.toModule(): Module? {
  return this.moduleInfo?.toModule()
}