/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("ProjectSystemUtil")

package com.android.tools.idea.projectsystem

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet
import java.nio.file.Path

/**
 * Provides a build-system-agnostic interface to the build system. Instances of this interface
 * only apply to a specific [Project].
 */
interface AndroidProjectSystem: ModuleHierarchyProvider {
  /**
   * Uses build-system-specific heuristics to locate the APK file produced by the given project, or null if none. The heuristics try
   * to determine the most likely APK file corresponding to the application the user is working on in the project's current configuration.
   */
  fun getDefaultApkFile(): VirtualFile?

  /**
   * Returns the absolute filesystem path to the aapt executable being used for the given project.
   */
  fun getPathToAapt(): Path

  /**
   * Initiates an incremental build of the entire project. Blocks the caller until the build
   * is completed.
   *
   * TODO: Make this asynchronous and return something like a ListenableFuture.
   */
  fun buildProject()

  /**
   * Returns true if the project allows adding new modules.
   */
  fun allowsFileCreation(): Boolean

  /**
   * Returns an interface for interacting with the given module.
   */
  fun getModuleSystem(module: Module): AndroidModuleSystem

  /**
   * Returns the best effort [ApplicationIdProvider] for the given project and [runConfiguration].
   *
   * Some project systems may be unable to retrieve the package name if no [runConfiguration] is provided or before
   * the project has been successfully built. The returned [ApplicationIdProvider] will throw [ApkProvisionException]'s
   * or return a name derived from incomplete configuration in this case.
   */
  @JvmDefault
  fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider? = null

  /**
   * Returns the [ApkProvider] for the given [runConfiguration].
   *
   * Returns `null`, if the project system does not recognize the [runConfiguration] as a supported one.
   */
  @JvmDefault
  fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? = null

  /**
   * Returns an instance of [ProjectSystemSyncManager] that applies to the project.
   */
  fun getSyncManager(): ProjectSystemSyncManager

  /**
   * [PsiElementFinder]s used with the given build system, e.g. for the R classes.
   *
   * These finders should not be registered as extensions
   */
  fun getPsiElementFinders(): Collection<PsiElementFinder>

  /**
   * [LightResourceClassService] instance used by this project system (if used at all).
   */
  fun getLightResourceClassService(): LightResourceClassService

  /**
   * [SourceProvidersFactory] instance used by the project system internally to re-instantiate the cached instance
   * when the structure of the project changes.
   */
  fun getSourceProvidersFactory(): SourceProvidersFactory

  /**
   * Returns a list of [AndroidFacet]s by given package name.
   */
  fun getAndroidFacetsWithPackageName(project: Project, packageName: String, scope: GlobalSearchScope): Collection<AndroidFacet>
}

val EP_NAME = ExtensionPointName<AndroidProjectSystemProvider>("com.android.project.projectsystem")

/**
 * Returns the instance of {@link AndroidProjectSystem} that applies to the given {@link Project}.
 */
fun Project.getProjectSystem(): AndroidProjectSystem {
  return ProjectSystemService.getInstance(this).projectSystem
}

/**
 * Returns the instance of [ProjectSystemSyncManager] that applies to the given [Project].
 */
fun Project.getSyncManager(): ProjectSystemSyncManager {
  return getProjectSystem().getSyncManager()
}

/**
 * Returns the instance of [AndroidModuleSystem] that applies to the given [Module].
 */
fun Module.getModuleSystem(): AndroidModuleSystem {
  return project.getProjectSystem().getModuleSystem(this)
}

/**
 * Returns the instance of [AndroidModuleSystem] that applies to the given [AndroidFacet].
 */
fun AndroidFacet.getModuleSystem(): AndroidModuleSystem {
  return module.project.getProjectSystem().getModuleSystem(module)
}

/**
 * Returns the instance of [AndroidModuleSystem] that applies to the given [PsiElement], if it can be determined.
 */
fun PsiElement.getModuleSystem(): AndroidModuleSystem? = ModuleUtilCore.findModuleForPsiElement(this)?.getModuleSystem()
