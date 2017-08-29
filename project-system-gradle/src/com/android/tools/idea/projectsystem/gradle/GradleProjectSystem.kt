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

import com.android.builder.model.AndroidProject.PROJECT_TYPE_APP
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.GoogleMavenVersionLookup
import com.android.tools.idea.templates.GradleFilePsiMerger
import com.android.tools.idea.templates.GradleFileSimpleMerger
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Contract
import java.nio.file.Path
import java.util.*

class GradleProjectSystem(val project: Project) : AndroidProjectSystem, AndroidProjectSystemProvider {
  val ID = "com.android.tools.idea.GradleProjectSystem"

  override val id: String
    get() = ID

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
  }

  override fun isApplicable(): Boolean {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle
  }

  override fun allowsFileCreation() = true

  override fun getDefaultApkFile(): VirtualFile? {
    return ModuleManager.getInstance(project).modules.asSequence()
        .mapNotNull { AndroidModuleModel.get(it) }
        .filter { it.androidProject.projectType == PROJECT_TYPE_APP }
        .flatMap { it.selectedVariant.mainArtifact.outputs.asSequence() }
        .map { it.mainOutputFile.outputFile }
        .find { it.exists() }
        ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

  override fun buildProject() {
    GradleProjectBuilder.getInstance(project).compileJava()
  }

  override fun syncProject(reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
    val syncResult = SettableFuture.create<AndroidProjectSystem.SyncResult>()

    if (GradleSyncState.getInstance(project).isSyncInProgress) {
      syncResult.setException(RuntimeException("A sync was requested while one is already in progress. Use"
          + "GradleSyncState.isSyncInProgress to detect this scenario."))
    }
    else if (project.isInitialized) {
      syncResult.setFuture(requestSync(reason, requireSourceGeneration))

    }
    else {
      StartupManager.getInstance(project).runWhenProjectIsInitialized {
        if (!GradleProjectInfo.getInstance(project).isNewOrImportedProject) {
          // http://b/62543184
          // If the project was created with the "New Project" wizard, there is no need to sync again.
          syncResult.setFuture(requestSync(reason, requireSourceGeneration))
        }
        else {
          syncResult.set(AndroidProjectSystem.SyncResult.SKIPPED)
        }
      }
    }

    return syncResult
  }

  @Contract(pure = true)
  private fun convertReasonToTrigger(reason: AndroidProjectSystem.SyncReason): GradleSyncStats.Trigger {
    return if (reason === AndroidProjectSystem.SyncReason.PROJECT_LOADED) {
      GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED
    }
    else if (reason === AndroidProjectSystem.SyncReason.PROJECT_MODIFIED) {
      GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
    }
    else {
      GradleSyncStats.Trigger.TRIGGER_USER_REQUEST
    }
  }

  private fun requestSync(reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
    val trigger = convertReasonToTrigger(reason)
    val syncResult = SettableFuture.create<AndroidProjectSystem.SyncResult>()

    val listener = object : GradleSyncListener.Adapter() {
      override fun syncSucceeded(project: Project) {
        syncResult.set(AndroidProjectSystem.SyncResult.SUCCESS)
      }

      override fun syncFailed(project: Project, errorMessage: String) {
        syncResult.set(AndroidProjectSystem.SyncResult.FAILURE)
      }

      override fun syncSkipped(project: Project) {
        syncResult.set(AndroidProjectSystem.SyncResult.SKIPPED)
      }
    }

    val request = GradleSyncInvoker.Request().setTrigger(trigger)
        .setGenerateSourcesOnSuccess(requireSourceGeneration).setRunInBackground(true)

    if (GradleProjectInfo.getInstance(project).isNewOrImportedProject) {
      request.setNewOrImportedProject()
    }

    try {
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener)
    }
    catch (t: Throwable) {
      syncResult.setException(t)
    }

    return syncResult
  }

  /**
   * This method will add the ":+" to the given dependency.
   * For Guava, for example: the dependency coordinate will not include the version:
   * com.google.guava:guava
   * and this method will add "+" as the version of the dependency to add.
   * @param dependency The dependency dependency without version.
   */
  override fun addDependency(module: Module, dependency: String) {
    val manager = GradleDependencyManager.getInstance(module.project)
    val coordinate = GradleCoordinate.parseCoordinateString(dependency + ":+")
    if (coordinate != null) {
      manager.ensureLibraryIsIncluded(module, listOf(coordinate), null)
    }
  }

  override fun mergeBuildFiles(dependencies: String,
                      destinationContents: String,
                      supportLibVersionFilter: String?): String {
    return if (project.isInitialized) {
      GradleFilePsiMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter)
    }
    else {
      GradleFileSimpleMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter)
    }
  }

  override val projectSystem = this

  override fun getModuleTemplates(module: Module, targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    return GradleAndroidModuleTemplate.getModuleTemplates(module, targetDirectory)
  }

  override fun addDependency(sourceContext: VirtualFile, artifact: GoogleMavenArtifact) {
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(sourceContext) ?:
        throw DependencyManagementException("Could not find module encapsulating source context $sourceContext")
    val buildModel = GradleBuildModel.get(module) ?:
        throw DependencyManagementException("Error getting build model of module ${module.name}")
    val gradleDependencyManager = GradleDependencyManager.getInstance(project)
    val listOfSingleCoordinate = Collections.singletonList(getGradleCoordinateForDependency(artifact))

    // Only add it if it doesn't already exist.
    if (gradleDependencyManager.findMissingDependencies(module, listOfSingleCoordinate).isNotEmpty()) {
      GradleDependencyManager.addDependenciesInTransaction(buildModel, module, listOfSingleCoordinate, null)
    }
  }

  override fun findArtifact(artifactId: GoogleMavenArtifactId): GoogleMavenArtifact? {
    // Here we add a ":+" to the end of the artifact string because GradleCoordinate.parseCoordinateString uses a regex matcher
    // that won't match a coordinate within just it's group and artifact id.  Adding a ":+" to the end in the case passes the
    // regex matcher and does not impact version lookup.
    val artifactString = artifactId.artifactString+":+"
    val coordinate = GradleCoordinate.parseCoordinateString(artifactString)
        ?: throw DependencyManagementException("Could not parse known artifact string $artifactString into gradle coordinate!")
    val version = GoogleMavenVersionLookup.findVersion(coordinate, null, allowPreview = false) ?: return null
    return GoogleMavenArtifact(artifactId, GradleDependencyVersion(version))
  }

  override fun getDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): GoogleMavenArtifact? {
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(sourceContext) ?:
        throw DependencyManagementException("Could not find module encapsulating source context $sourceContext")
    val androidModuleModel = AndroidModuleModel.get(module) ?:
        throw DependencyManagementException("Could not find android module model for module $module")

    // Check for android library dependencies from the build model
    androidModuleModel.selectedMainCompileLevel2Dependencies.androidLibraries
        .asSequence()
        .mapNotNull { GradleCoordinate.parseCoordinateString(it.artifactAddress) }
        .mapNotNull { getDependencyForGradleCoordinate(it)}
        .find { it.artifactId == artifactId }
        ?.let { return it }

    // Check for compile dependencies from the gradle build file
    val configurationName = GradleUtil.mapConfigurationName(CommonConfigurationNames.COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false)

    return GradleBuildModel.get(module)?.let {
      it.dependencies().artifacts(configurationName)
          .filter { artifactId.artifactString == "${it.group().value()}:${it.name().value()}" }
          .map { GoogleMavenArtifact(artifactId, parseDependencyVersion(it.version().value())) }
          .firstOrNull()
    }
  }

  private fun parseDependencyVersion(version: String?): GradleDependencyVersion {
    if (version == null) return GradleDependencyVersion(null)
    return GradleDependencyVersion(GradleVersion.parse(version))
  }

  private fun getDependencyFile(location: VirtualFile): VirtualFile? {
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(location) ?: return null
    return GradleUtil.getGradleBuildFile(module)
  }

  companion object {
    fun getGradleCoordinateForDependency(artifact: GoogleMavenArtifact): GradleCoordinate? {
      val artifactString = artifact.artifactId.artifactString
      val coordinateString = "$artifactString:${artifact.version.getMavenVersion().toString()}"
      return GradleCoordinate.parseCoordinateString(coordinateString)
    }

    fun getDependencyForGradleCoordinate(coordinate: GradleCoordinate): GoogleMavenArtifact? {
      val artifactString = "${coordinate.groupId}:${coordinate.artifactId}"
      return GoogleMavenArtifactId.values()
          .find { it.artifactString == artifactString }
          ?.let { GoogleMavenArtifact(it, GradleDependencyVersion(coordinate.version)) }
    }
  }
}
