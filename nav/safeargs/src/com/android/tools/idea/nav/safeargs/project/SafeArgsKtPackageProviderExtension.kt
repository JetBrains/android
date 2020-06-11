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
package com.android.tools.idea.nav.safeargs.project

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.psi.kotlin.SafeArgsKotlinPackageDescriptor
import com.android.tools.idea.nav.safeargs.psi.kotlin.SafeArgSyntheticPackageResourceData
import com.android.tools.idea.nav.safeargs.module.SafeArgsResourceForKtDescriptors
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.idea.core.unwrapModuleSourceInfo
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.storage.StorageManager

/**
 * Safe Args Kotlin Synthetic module package provider
 */
class SafeArgsKtPackageProviderExtension(val project: Project) : PackageFragmentProviderExtension {
  override fun getPackageFragmentProvider(project: Project,
                                          module: ModuleDescriptor,
                                          storageManager: StorageManager,
                                          trace: BindingTrace,
                                          moduleInfo: ModuleInfo?,
                                          lookupTracker: LookupTracker): PackageFragmentProvider? {
    if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return null

    val moduleSourceInfo = moduleInfo?.unwrapModuleSourceInfo()?.takeIf { it.platform.isJvm() } ?: return null
    val facet = moduleSourceInfo.module.let { AndroidFacet.getInstance(it) } ?: return null
    if (facet.safeArgsMode != SafeArgsMode.KOTLIN) return null

    val moduleNavResources = SafeArgsResourceForKtDescriptors.getInstance(facet.module).getNavResource()

    val packageFqName = getPackageName(facet)?.let {
      FqName(it)
    } ?: return null

    val packageResourceData = SafeArgSyntheticPackageResourceData(moduleNavResources)
    // TODO(b/157918926) Cache package descriptors
    val packageDescriptor = SafeArgsKotlinPackageDescriptor(module, packageFqName, packageResourceData, storageManager)

    return SafeArgSyntheticPackageProvider(packageDescriptor, packageFqName, facet.module)
  }
}

class SafeArgSyntheticPackageProvider(
  private val packageDescriptorProvider: PackageFragmentDescriptor,
  private val packageFqName: FqName,
  private val module: Module
) : PackageFragmentProvider {
  override fun getPackageFragments(fqName: FqName): List<PackageFragmentDescriptor> {
    // If this package is the currently resolving package, descriptors from this package are returned.
    if (packageFqName == fqName) {
      return listOf(packageDescriptorProvider)
    }

    // If this package is directly depended by the currently resolving package, descriptors from this package are
    // returned.
    val project = module.project
    project.getProjectSystem()
      .getAndroidFacetsWithPackageName(project, fqName.asString(), GlobalSearchScope.projectScope(project))
      .asSequence()
      .mapNotNull { ModuleRootManager.getInstance(it.module) }
      .firstOrNull {
        it.isDependsOn(module)
      } ?: return emptyList()

    return listOf(packageDescriptorProvider)
  }

  override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean) = emptyList<FqName>()
}