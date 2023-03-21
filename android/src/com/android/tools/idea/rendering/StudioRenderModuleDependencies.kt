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

import com.android.SdkConstants
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.requiresAndroidModel
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.util.dependsOn
import com.android.tools.idea.util.dependsOnAndroidx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.IOException

/** Studio specific implementation of [RenderModuleDependencies]. */
class StudioRenderModuleDependencies(private val module: Module) : RenderModuleDependencies {
  override val dependsOnAppCompat: Boolean
    get() = module.dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7)

  override val dependsOnAndroidXAppCompat: Boolean
    get() = module.dependsOn(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7)

  override val dependsOnAndroidX: Boolean
    get() = module.dependsOnAndroidx()

  override fun reportMissingSdkDependency(logger: IRenderLogger) {
    val message = RenderProblem.create(ProblemSeverity.ERROR)
    logger.addMessage(message)
    message.htmlBuilder.addLink("No Android SDK found. Please ", "configure", " an Android SDK.",
                                logger.linkManager.createRunnableLink {
                                  val project = module.project
                                  val service = ProjectSettingsService.getInstance(project)
                                  if (project.requiresAndroidModel() && service is AndroidProjectSettingsService) {
                                    (service as AndroidProjectSettingsService).openSdkSettings()
                                    return@createRunnableLink
                                  }
                                  AndroidSdkUtils.openModuleDependenciesConfigurable(module)
                                })
  }

  override val rClassesNames: List<String>
    get() =
      (
        (
          sequenceOf(module) +
          // Get all project (not external libraries) dependencies
          AndroidDependenciesCache.getAllAndroidDependencies(module, false).map { it.module }
        ).map { getRClassName(it) } +
        // Get all external (libraries) dependencies
        module.getModuleSystem().getAndroidLibraryDependencies(DependencyScopeType.MAIN).map { getPackageName(it) }
      ).filterNotNull().toList()

  override fun findPsiClassInModuleAndDependencies(fqcn: String): PsiClass? {
    val facade = JavaPsiFacade.getInstance(module.project)
    return facade.findClass(fqcn, module.getModuleWithDependenciesAndLibrariesScope(false))
  }
}

private fun getRClassName(module: Module): String? = module.getModuleSystem().getPackageName()?.let { "$it.${SdkConstants.R_CLASS}" }

private fun getPackageName(library: ExternalAndroidLibrary): String? {
  var packageName = library.packageName
  if (packageName == null) {
    // Try the manifest if the package name is not directly set.
    val manifest = library.manifestFile
    if (manifest != null) {
      try {
        packageName = AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifest)
      }
      catch (e: IOException) {
        Logger.getInstance(StudioRenderModuleDependencies::class.java)
          .info("getPackageName: failed to find packageName for library ${library.libraryName()}")
      }
    }
    if (packageName == null) {
      return null
    }
  }
  return packageName + '.' + SdkConstants.R_CLASS
}