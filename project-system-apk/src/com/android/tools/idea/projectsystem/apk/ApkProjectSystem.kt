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
package com.android.tools.idea.projectsystem.apk

import com.android.ddmlib.Client
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.execution.common.debug.utils.FacetFinder
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider
import com.android.tools.idea.projectsystem.LightResourceClassService
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemToken
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AndroidInnerClassFinder
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.FileSystemApkProvider
import com.android.tools.idea.run.NonGradleApplicationIdProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.util.toVirtualFile
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import com.intellij.ui.AppUIUtil
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.nio.file.Path
import java.util.IdentityHashMap

class ApkProjectSystem(override val project: Project) : AndroidProjectSystem {
  private val delegate = DefaultProjectSystem(project)

  override fun getBootClasspath(module: Module): Collection<String> =
    delegate.getBootClasspath(module)

  override fun getBuildManager(): ProjectSystemBuildManager =
    delegate.getBuildManager()

  override fun getClassJarProvider(): ClassJarProvider =
    delegate.getClassJarProvider()

  override fun findModulesWithApplicationId(applicationId: String): Collection<Module> =
    delegate.findModulesWithApplicationId(applicationId)

  override val submodules = project.modules.toList()
    .filter { ModuleRootManager.getInstance(it).sourceRoots.isNotEmpty() || ApkFacet.getInstance(it) != null }

  override fun getSyncManager(): ProjectSystemSyncManager = object : ProjectSystemSyncManager {
    override fun syncProject(reason: ProjectSystemSyncManager.SyncReason): ListenableFuture<ProjectSystemSyncManager.SyncResult> {
      AppUIUtil.invokeLaterIfProjectAlive(project) {
        project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
      }
      return Futures.immediateFuture(ProjectSystemSyncManager.SyncResult.SUCCESS)
    }
    override fun isSyncInProgress() = false
    override fun isSyncNeeded() = false
    override fun getLastSyncResult() = ProjectSystemSyncManager.SyncResult.SUCCESS
  }

  override fun getDefaultApkFile(): VirtualFile? =
    ProjectFacetManager.getInstance(project).getFacets(ApkFacet.getFacetTypeId())
      .firstNotNullOfOrNull { facet -> facet?.configuration?.APK_PATH?.let { File(it).toVirtualFile() } }

  override fun getPathToAapt(): Path =
    AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(ApkProjectSystem::class.java))

  override fun allowsFileCreation(): Boolean = false

  override fun getSourceProvidersFactory(): SourceProvidersFactory =
    delegate.getSourceProvidersFactory()

  override fun getModuleSystem(module: Module): AndroidModuleSystem = ApkModuleSystem(module)

  override fun getPsiElementFinders(): Collection<PsiElementFinder> = listOf(
    AndroidInnerClassFinder.INSTANCE,
    AndroidManifestClassPsiElementFinder.getInstance(project),
    AndroidResourceClassPsiElementFinder(getLightResourceClassService())
  )

  override fun getLightResourceClassService(): LightResourceClassService = ProjectLightResourceClassService.getInstance(project)

  override fun isNamespaceOrParentPackage(packageName: String): Boolean =
    delegate.isNamespaceOrParentPackage(packageName)

  override fun getAndroidFacetsWithPackageName(project: Project, packageName: String): Collection<AndroidFacet> =
    delegate.getAndroidFacetsWithPackageName(project, packageName)

  override fun getKnownApplicationIds(): Set<String> =
    // TODO: properties.USE_CUSTOM_MANIFEST_PACKAGE?
    ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).asSequence()
      .mapNotNull { it.getModuleSystem().getPackageName()?.takeIf(CharSequence::isNotEmpty) }
      .toImmutableSet()

  override fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? {
    val module = (runConfiguration as? ModuleBasedConfiguration<*,*>)?.configurationModule?.module ?: return null
    val apkFacet = ApkFacet.getInstance(module) ?: return null
    return FileSystemApkProvider(module, File(apkFacet.configuration.APK_PATH))
  }

  override fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider? {
    val module = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module ?: return null
    val androidFacet = AndroidFacet.getInstance(module) ?: return null
    return NonGradleApplicationIdProvider(androidFacet)
  }

  override fun validateRunConfiguration(runConfiguration: RunConfiguration): List<ValidationError> = listOf()
  override fun validateRunConfiguration(runConfiguration: RunConfiguration, quickFixCallback: Runnable?): List<ValidationError> = listOf()

  override fun isAndroidProject() = true
}

interface ApkToken : ProjectSystemToken {
  override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean = projectSystem is ApkProjectSystem
}

class ApkApplicationProjectContextProvider(val project: Project) : ApplicationProjectContextProvider, ApkToken {
  override fun getApplicationProjectContext(client: Client): ApplicationProjectContext? {
    val result = FacetFinder.tryFindFacetForProcess(project, client.clientData) ?: return null
    return FacetBasedApplicationProjectContext(
      result.applicationId,
      result.facet
    )
  }
}