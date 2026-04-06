/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.projectsystem.TestResourceResolutionToken
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.util.dependsOn
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ViewClass
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import java.io.IOException

/** Studio specific implementation of [ModuleDependencies]. */
class StudioModuleDependencies(private val module: Module) : ModuleDependencies {
  override fun dependsOn(artifactId: GoogleMavenArtifactId): Boolean = module.dependsOn(artifactId)

  override fun dependsOnAndroidx(): Boolean = module.getModuleSystem().useAndroidX

  override fun getResourcePackageNames(includeExternalLibraries: Boolean): List<String> {
    val moduleSystem = module.getModuleSystem()
    val mainModule = moduleSystem.getProductionAndroidModule()

    val projectDependencies = buildSet {
      add(module)
      if (mainModule != null && mainModule != module) {
        add(mainModule)
      }
      addAll(AndroidDependenciesCache.getAllAndroidDependencies(module, false).map { it.module })
      addAll(moduleSystem.getResourceModuleDependencies())

      addAll(TestResourceResolutionToken.getAdditionalLocalResourceDependencies(moduleSystem))
    }

    val externalDependencies =
      if (includeExternalLibraries) {
        // Note: findDependenciesWithResources internally delegates to FindDependenciesWithResourcesToken
        // which automatically detects the correct scope (e.g., MAIN, ANDROID_TEST, SCREENSHOT_TEST)
        // based on the module, ensuring test dependencies are safely isolated.
        com.android.tools.idea.findDependenciesWithResources(module).values
      } else {
        emptyList()
      }

    val packages = LinkedHashSet<String>()
    for (projectDep in projectDependencies) {
      projectDep.getModuleSystem().getPackageName()?.let { packages.add(it) }
    }
    for (extDep in externalDependencies) {
      getPackageName(extDep)?.let { packages.add(it) }
    }
    return packages.toList()
  }

  override fun findViewClass(fqcn: String): ViewClass? {
    val facade = JavaPsiFacade.getInstance(module.project)
    return facade.findClass(fqcn, module.getModuleWithDependenciesAndLibrariesScope(false))?.let { PsiClassViewClass(it) }
  }
}

private fun getPackageName(library: ExternalAndroidLibrary): String? {
  var packageName = library.packageName
  if (packageName == null) {
    // Try the manifest if the package name is not directly set.
    val manifest = library.manifestFile
    if (manifest != null) {
      try {
        packageName = AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifest)
      } catch (e: IOException) {
        Logger.getInstance(StudioModuleDependencies::class.java)
          .info("getPackageName: failed to find packageName for library ${library.libraryName()}")
      }
    }
    if (packageName == null) {
      return null
    }
  }
  return packageName
}
