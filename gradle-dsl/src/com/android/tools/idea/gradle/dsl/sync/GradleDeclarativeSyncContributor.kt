// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.sync

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.model.BuildModelContext.ResolvedConfigurationFileLocationProvider
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl
import com.google.common.base.Strings.isNullOrEmpty
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleDeclarativeEntitySource
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.DECLARATIVE_CONTRIBUTOR)
class GradleDeclarativeSyncContributor : GradleSyncContributor {
  override suspend fun onResolveProjectInfoStarted(context: ProjectResolverContext, storage: MutableEntityStorage) {
    if (context.isPhasedSyncEnabled()) {
      configureProject(context, storage)
    }
  }

  override suspend fun onModelFetchPhaseCompleted(context: ProjectResolverContext,
                                                  storage: MutableEntityStorage,
                                                  phase: GradleModelFetchPhase) {
    // remove model
  }

  private suspend fun configureProject(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val projectRootPath = Path.of(context.projectPath)
    val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val entitySource = GradleDeclarativeEntitySource(projectRootUrl)
    val declarativeGradleBuildFile = File(context.projectPath, "build.gradle.dcl")
    if(!declarativeGradleBuildFile.isFile) return

    val virtualBuildFile = VfsUtil.findFileByIoFile(declarativeGradleBuildFile, false)

    val androidContext = BuildModelContext.create(project, createBuildModelContext())

    Registry.Companion.get("gradle.declarative.studio.support").setValue(true)

    val projectBuildModel = ProjectBuildModelImpl(project, virtualBuildFile, androidContext)
    val modulePaths = HashSet<String>()
    val projectName = entitySource.projectRootUrl.fileName // settings model does not contain the project name
    val settingsModel = projectBuildModel.projectSettingsModel
    if(settingsModel != null) {
      modulePaths.addAll(settingsModel.modulePaths())
    }

    val buildModel = projectBuildModel.projectBuildModel
    if(buildModel != null) {
      val javaModel = buildModel.javaApplication()
      val languageLevel = javaModel.javaVersion().toLanguageLevel()
      val mainClass = javaModel.mainClass()

      val dependenciesModel = javaModel.dependencies() //TODO convert android dependencies to workspace deps

      val moduleEntity = addModuleEntity(storage, entitySource, projectName, listOf())
      addContentRootEntity(storage, entitySource, moduleEntity, projectRootUrl)

      //assertEquals("com.google.guava:guava:32.1.3-jre", dependenciesModel.artifacts().get(0).compactNotation());
    }
  }

  private fun addModuleEntity(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    moduleName: String,
    dependencies: List<ModuleDependencyItem>
  ): ModuleEntity.Builder {
    val moduleName = moduleName
    val moduleEntity = ModuleEntity(
      name = moduleName,
      entitySource = entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    )
    storage addEntity moduleEntity
    return moduleEntity
  }

  private fun addContentRootEntity(
    storage: MutableEntityStorage,
    entitySource: GradleDeclarativeEntitySource,
    moduleEntity: ModuleEntity.Builder,
    url: VirtualFileUrl
  ) {
    storage addEntity ContentRootEntity(
      url = url,
      entitySource = entitySource,
      excludedPatterns = emptyList()
    ) {
      module = moduleEntity
    }
  }

  private fun createBuildModelContext(): ResolvedConfigurationFileLocationProvider {
    return object : ResolvedConfigurationFileLocationProvider {
      override fun getGradleBuildFile(module: Module): VirtualFile? {
        // Resolved location is unknown (no sync).
        return null
      }

      override fun getGradleProjectRootPath(module: Module): @SystemIndependent String? {
        val linkedProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        if (!isNullOrEmpty(linkedProjectPath)) {
          return linkedProjectPath
        }
        val moduleFilePath: @SystemIndependent String = module.getModuleFilePath()
        return VfsUtil.getParentDir(moduleFilePath)
      }

      override fun getGradleProjectRootPath(project: Project): @SystemIndependent String? {
        val projectDir = project.guessProjectDir()
        if (projectDir == null) return null
        return projectDir.getPath()
      }
    }
  }
}

