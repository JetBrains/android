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
package com.android.tools.idea.projectsystem.gradle

import com.android.ide.common.gradle.model.IdeAndroidProjectType
import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.sdklib.AndroidVersion
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.run.PostBuildModelProvider
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.OutputType
import com.android.tools.idea.gradle.util.getOutputFileOrFolderFromListingFile
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.logManifestIndexQueryError
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.projectsystem.SourceProvidersImpl
import com.android.tools.idea.projectsystem.createSourceProvidersForLegacyModule
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.res.AndroidInnerClassFinder
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.GradleApkProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.createIdeaSourceProviderFromModelSourceProvider
import java.nio.file.Path
import java.util.function.Function

class GradleProjectSystem(val project: Project) : AndroidProjectSystem {
  private val moduleHierarchyProvider: GradleModuleHierarchyProvider = GradleModuleHierarchyProvider(project)
  private val mySyncManager: ProjectSystemSyncManager = GradleProjectSystemSyncManager(project)
  private val myBuildManager: ProjectSystemBuildManager = GradleProjectSystemBuildManager(project)
  private val myProjectBuildModelHandler: ProjectBuildModelHandler = ProjectBuildModelHandler.getInstance(project)

  private val myPsiElementFinders: List<PsiElementFinder> = run {
    listOf(
      AndroidInnerClassFinder.INSTANCE,
      AndroidManifestClassPsiElementFinder.getInstance(project),
      AndroidResourceClassPsiElementFinder(getLightResourceClassService())
    )
  }

  override fun getSyncManager(): ProjectSystemSyncManager = mySyncManager
  override fun getBuildManager(): ProjectSystemBuildManager = myBuildManager

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
  }

  override fun allowsFileCreation() = true

  override fun getDefaultApkFile(): VirtualFile? {
    return ModuleManager.getInstance(project).modules.asSequence()
      .mapNotNull { AndroidModuleModel.get(it) }
      .filter { it.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }
      .flatMap { androidModel ->
        @Suppress("DEPRECATION")
        if (androidModel.features.isBuildOutputFileSupported) {
          sequenceOf(androidModel.selectedVariant.mainArtifact.buildInformation.getOutputFileOrFolderFromListingFile(OutputType.Apk))
        }
        else {
          androidModel.selectedVariant.mainArtifact.outputs.asSequence().map { it.outputFile }
        }
      }
      .filterNotNull()
      .find { it.exists() }
      ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    return GradleModuleSystem(module, myProjectBuildModelHandler, moduleHierarchyProvider.createForModule(module))
  }

  override fun getApplicationIdProvider(runConfiguration: RunConfiguration): GradleApplicationIdProvider? {
    val androidFacet = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.androidFacet ?: return null
    return GradleApplicationIdProvider(
      androidFacet,
      PostBuildModelProvider { (runConfiguration as? UserDataHolder)?.getUserData(GradleApkProvider.POST_BUILD_MODEL) }
    )
  }

  override fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? {
    val module = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module ?: return null
    if (runConfiguration !is AndroidRunConfigurationBase) return null
    val facet = AndroidFacet.getInstance(module)!!

    fun outputKind(targetDevicesMinVersion: AndroidVersion?): GradleApkProvider.OutputKind {
      return when (DynamicAppUtils.useSelectApksFromBundleBuilder(facet.module, runConfiguration, targetDevicesMinVersion)) {
        true -> GradleApkProvider.OutputKind.AppBundleOutputModel
        false -> GradleApkProvider.OutputKind.Default
      }
    }

    return GradleApkProvider(
      facet,
      getApplicationIdProvider(runConfiguration) ?: return null,
      PostBuildModelProvider { runConfiguration.getUserData(GradleApkProvider.POST_BUILD_MODEL) },
      runConfiguration.isTestConfiguration,
      Function{ outputKind(it) }
    )
  }

  override fun getPsiElementFinders(): List<PsiElementFinder> = myPsiElementFinders

  override fun getLightResourceClassService() = ProjectLightResourceClassService.getInstance(project)

  override val submodules: Collection<Module>
    get() = moduleHierarchyProvider.forProject.submodules

  override fun getSourceProvidersFactory(): SourceProvidersFactory = object : SourceProvidersFactory {
    override fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders? {
      val model = AndroidModuleModel.get(facet)
      return if (model != null) createSourceProvidersFromModel(model) else createSourceProvidersForLegacyModule(facet)
    }
  }

  override fun getAndroidFacetsWithPackageName(project: Project, packageName: String, scope: GlobalSearchScope): List<AndroidFacet> {
    if (AndroidManifestIndex.indexEnabled()) {
      try {
        return DumbService.getInstance(project).runReadActionInSmartMode<List<AndroidFacet>>(Computable {
          AndroidManifestIndex.queryByPackageName(project, packageName, scope)
        })
      }
      catch (e: IndexNotReadyException) {
        // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
        //  We need to refactor the callers of this to require a *smart*
        //  read action, at which point we can remove this try-catch.
        logManifestIndexQueryError(e)
      }
    }

    return ProjectFacetManager.getInstance(project)
      .getFacets(AndroidFacet.ID)
      .asSequence()
      .filter { getPackageName(it) == packageName }
      .filter { facet -> facet.sourceProviders.mainManifestFile?.let(scope::contains) == true }
      .toList()
  }
}

fun createSourceProvidersFromModel(model: AndroidModuleModel): SourceProviders {
  val all =
    @Suppress("DEPRECATION")
    (
      model.allSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.MAIN) } +
      model.allUnitTestSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.UNIT_TEST) } +
      model.allAndroidTestSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.ANDROID_TEST) }
    )

  fun IdeSourceProvider.toIdeaSourceProvider(): NamedIdeaSourceProvider {
    if (!all.containsKey(this)) {
      println("Does not contain: $this")
    }
    return all.getValue(this)
  }

  return SourceProvidersImpl(
    mainIdeaSourceProvider = model.defaultSourceProvider.toIdeaSourceProvider(),
    currentSourceProviders = @Suppress("DEPRECATION") model.activeSourceProviders.map { it.toIdeaSourceProvider() },
    currentUnitTestSourceProviders = @Suppress("DEPRECATION") model.unitTestSourceProviders.map { it.toIdeaSourceProvider() },
    currentAndroidTestSourceProviders = @Suppress("DEPRECATION") model.androidTestSourceProviders.map { it.toIdeaSourceProvider() },
    currentAndSomeFrequentlyUsedInactiveSourceProviders = @Suppress("DEPRECATION") model.allSourceProviders.map { it.toIdeaSourceProvider() },
    mainAndFlavorSourceProviders =
    run {
      val flavorNames = model.selectedVariant.productFlavors.toSet()
      listOf(model.defaultSourceProvider.toIdeaSourceProvider()) +
      model.androidProject.productFlavors
        .filter { it.productFlavor.name in flavorNames }
        .map { it.sourceProvider.toIdeaSourceProvider() }
    }
  )
}

