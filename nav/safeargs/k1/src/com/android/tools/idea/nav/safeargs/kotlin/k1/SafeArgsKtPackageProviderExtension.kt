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
package com.android.tools.idea.nav.safeargs.kotlin.k1

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.PackageFragmentProviderOptimized
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.storage.StorageManager

/** Safe Args Kotlin Synthetic module package provider */
class SafeArgsKtPackageProviderExtension(val project: Project) : PackageFragmentProviderExtension {
  override fun getPackageFragmentProvider(
    project: Project,
    module: ModuleDescriptor,
    storageManager: StorageManager,
    trace: BindingTrace,
    moduleInfo: ModuleInfo?,
    lookupTracker: LookupTracker,
  ): PackageFragmentProvider? {
    val facet = moduleInfo?.toModule()?.let { AndroidFacet.getInstance(it) } ?: return null
    if (facet.safeArgsMode != SafeArgsMode.KOTLIN) return null

    val packageDescriptors =
      KtDescriptorCacheModuleService.getInstance(facet.module).getDescriptors(module).takeIf {
        it.isNotEmpty()
      } ?: return null
    return SafeArgsSyntheticPackageProvider(packageDescriptors)
  }
}

class SafeArgsSyntheticPackageProvider(
  private val packageDescriptorProvider: Map<FqName, List<PackageFragmentDescriptor>>
) : PackageFragmentProviderOptimized {
  override fun collectPackageFragments(
    fqName: FqName,
    packageFragments: MutableCollection<PackageFragmentDescriptor>,
  ) {
    val descriptors = packageDescriptorProvider[fqName] ?: return
    packageFragments.addAll(descriptors)
  }

  override fun getPackageFragments(fqName: FqName): List<PackageFragmentDescriptor> {
    return packageDescriptorProvider[fqName] ?: emptyList()
  }

  override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): List<FqName> {
    return packageDescriptorProvider
      .asSequence()
      .filter { (k, _) -> !k.isRoot && k.parent() == fqName }
      .mapTo(mutableListOf()) { it.key }
  }

  override fun isEmpty(fqName: FqName): Boolean {
    return packageDescriptorProvider[fqName].isNullOrEmpty()
  }
}
