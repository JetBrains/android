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
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.GoogleMavenVersionLookup
import com.android.tools.idea.templates.GradleFilePsiMerger
import com.android.tools.idea.templates.GradleFileSimpleMerger
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
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
    return syncProject(project, reason, requireSourceGeneration)
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

  override fun canGeneratePngFromVectorGraphics(module: Module): CapabilityStatus {
    return supportsPngGeneration(module)
  }

  override fun addDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId, version: GoogleMavenArtifactVersion?) {
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(sourceContext) ?:
        throw DependencyManagementException("Could not find module containing source file $sourceContext")
    val buildModel = GradleBuildModel.get(module) ?:
        throw DependencyManagementException("Error getting build model of module ${module.name}")

    val gradleVersion = if (version == null) {
      // Here we add a ":+" to the end of the artifact string because GradleCoordinate.parseCoordinateString uses a regex matcher
      // that won't match a coordinate within just it's group and artifact id.  Adding a ":+" to the end in the case passes the
      // regex matcher and does not impact version lookup.
      val artifactCoordinate = artifactId.artifactCoordinate + ":+"
      val coordinate = GradleCoordinate.parseCoordinateString(artifactCoordinate)
          ?: throw DependencyManagementException("Could not parse known artifact string $artifactCoordinate into gradle coordinate!")
      GoogleMavenVersionLookup.findVersion(coordinate, null, allowPreview = false)
          ?: throw DependencyManagementException("Could not find an $coordinate artifact for addition!")
    }
    else {
      version.getMavenVersion() ?: throw DependencyManagementException("Gradle version is not specified.")
    }

    val gradleDependencyManager = GradleDependencyManager.getInstance(project)
    val coordinateToAdd = GradleCoordinate.parseCoordinateString("${artifactId.artifactCoordinate}:$gradleVersion")
    val singleCoordinateList = Collections.singletonList(coordinateToAdd)

    // Only add it if it doesn't already exist.
    if (gradleDependencyManager.findMissingDependencies(module, singleCoordinateList).isNotEmpty()) {
      GradleDependencyManager.addDependenciesInTransaction(buildModel, module, singleCoordinateList, null)
    }
  }

  override fun getVersionOfDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion? {
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(sourceContext) ?:
        throw DependencyManagementException("Could not find module encapsulating source context $sourceContext")
    val androidModuleModel = AndroidModuleModel.get(module) ?:
        throw DependencyManagementException("Could not find android module model for module $module")

    // Check for android library dependencies from the build model
    androidModuleModel.selectedMainCompileLevel2Dependencies.androidLibraries
        .asSequence()
        .mapNotNull { GradleCoordinate.parseCoordinateString(it.artifactAddress) }
        .find { "${it.groupId}:${it.artifactId}" == artifactId.artifactCoordinate }
        ?.let { return GradleDependencyVersion(it.version) }

    // Check for compile dependencies from the gradle build file
    val configurationName = GradleUtil.mapConfigurationName(CommonConfigurationNames.COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false)

    return GradleBuildModel.get(module)?.let {
      it.dependencies().artifacts(configurationName)
          .filter { artifactId.artifactCoordinate == "${it.group().value()}:${it.name().value()}" }
          .map { parseDependencyVersion(it.version().value()) }
          .firstOrNull()
    }
  }

  private fun parseDependencyVersion(version: String?): GradleDependencyVersion {
    if (version == null) return GradleDependencyVersion(null)
    return GradleDependencyVersion(GradleVersion.parse(version))
  }
}
