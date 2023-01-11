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
package com.android.tools.idea.project

import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.navigator.getSubmodules
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.res.AndroidInnerClassFinder
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.FileSystemApkProvider
import com.android.tools.idea.run.NonGradleApkProvider
import com.android.tools.idea.run.NonGradleApplicationIdProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AppUIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.createSourceProvidersForLegacyModule
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Path
import java.util.IdentityHashMap

/**
 * This implementation of AndroidProjectSystem is used for projects where the build system is not
 * recognized. It provides a minimal set of capabilities and opts out of most optional behaviors.
 */
class DefaultProjectSystem(val project: Project) : AndroidProjectSystem, AndroidProjectSystemProvider {
  override val id: String = ""

  override fun getDefaultApkFile(): VirtualFile? = null

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(DefaultProjectSystem::class.java))
  }

  override fun isApplicable() = false

  override fun allowsFileCreation() = false

  override fun getSyncManager(): ProjectSystemSyncManager = object: ProjectSystemSyncManager {
    override fun syncProject(reason: SyncReason): ListenableFuture<SyncResult> {
      AppUIUtil.invokeLaterIfProjectAlive(project) {
        project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
      }
      return Futures.immediateFuture(SyncResult.SUCCESS)
    }

    override fun isSyncInProgress() = false
    override fun isSyncNeeded() = false
    override fun getLastSyncResult() = SyncResult.SUCCESS
  }

  override fun getBuildManager(): ProjectSystemBuildManager = DefaultBuildManager

  override val projectSystem = this

  private val moduleCache: MutableMap<Module, AndroidModuleSystem> = IdentityHashMap()
  override fun getModuleSystem(module: Module): AndroidModuleSystem = moduleCache.getOrPut(module, { DefaultModuleSystem(module) })

  @TestOnly
  fun setModuleSystem(module: Module, moduleSystem: AndroidModuleSystem) {
    moduleCache[module] = moduleSystem
  }

  override fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider? {
    val module = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module ?: return null
    return NonGradleApplicationIdProvider(
      AndroidFacet.getInstance(module) ?: throw IllegalStateException("Cannot find AndroidFacet. Module: ${module.name}"))
  }

  override fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? {
    val module = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module ?: return null
    val forTests: Boolean = (runConfiguration as? AndroidRunConfigurationBase)?.isTestConfiguration ?: false
    val facet = AndroidFacet.getInstance(module)!!
    val applicationIdProvider = getApplicationIdProvider(runConfiguration) ?: return null
    if (forTests) {
      return NonGradleApkProvider(facet, applicationIdProvider, null)
    }
    val apkFacet = ApkFacet.getInstance(module)
    return when {
      apkFacet != null -> FileSystemApkProvider(apkFacet.module, File(apkFacet.configuration.APK_PATH))
      runConfiguration is AndroidRunConfiguration -> NonGradleApkProvider(facet, applicationIdProvider, runConfiguration.ARTIFACT_NAME)
      else -> null
    }
  }

  override fun validateRunConfiguration(runConfiguration: RunConfiguration): List<ValidationError> {
    return emptyList()
  }

  override fun getPsiElementFinders(): List<PsiElementFinder> {
    return listOf(
      AndroidInnerClassFinder.INSTANCE,
      AndroidManifestClassPsiElementFinder.getInstance(project),
      AndroidResourceClassPsiElementFinder(getLightResourceClassService())
    )
  }

  override fun getLightResourceClassService() = ProjectLightResourceClassService.getInstance(project)

  override val submodules: Collection<Module>
    get() = getSubmodules(project, null)

  override fun getClassJarProvider(): ClassJarProvider {
    return object: ClassJarProvider {
      override fun getModuleExternalLibraries(module: Module): List<File> {
        return AndroidRootUtil.getExternalLibraries(module).map { file: VirtualFile? -> VfsUtilCore.virtualToIoFile(file!!) }
      }

    }
  }

  override fun getSourceProvidersFactory(): SourceProvidersFactory = object : SourceProvidersFactory {
    override fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders? {
      return createSourceProvidersForLegacyModule(facet)
    }
  }

  override fun getAndroidFacetsWithPackageName(project: Project, packageName: String): Collection<AndroidFacet> {
    // TODO(b/148300410)
    val projectScope = GlobalSearchScope.projectScope(project)
    return ProjectFacetManager.getInstance(project)
      .getFacets(AndroidFacet.ID)
      .asSequence()
      .filter { it.getModuleSystem().getPackageName() == packageName }
      .filter { facet -> facet.sourceProviders.mainManifestFile?.let(projectScope::contains) == true }
      .toList()
  }

  override fun desugarLibraryConfigFiles(project: Project): List<File> {
    return listOf()
  }
}
