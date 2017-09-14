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
package com.android.tools.idea.common.projectsystem

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.projectsystem.*
import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
class TestProjectSystem : AndroidProjectSystem, AndroidProjectSystemProvider {

  data class Artifact(val id: GoogleMavenArtifactId, val version: GoogleMavenArtifactVersion)

  override fun canGeneratePngFromVectorGraphics(module: Module): CapabilityStatus {
    TODO("not implemented")
  }

  private val dependenciesBySource: HashMultimap<VirtualFile, Artifact> = HashMultimap.create()
  private val knownArtifactVersions: MutableMap<GoogleMavenArtifactId, TestDependencyVersion> = HashMap()

  override val id: String = "com.android.tools.idea.common.projectsystem.TestProjectSystem"

  override val projectSystem = this

  override fun allowsFileCreation(): Boolean = false

  override fun getDefaultApkFile(): VirtualFile? = null

  override fun getPathToAapt(): Path {
    TODO("not implemented")
  }

  override fun isApplicable(): Boolean = true

  override fun buildProject() {
    TODO("not implemented")
  }

  override fun syncProject(reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
    return Futures.immediateFuture(AndroidProjectSystem.SyncResult.FAILURE)
  }

  override fun addDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId, version: GoogleMavenArtifactVersion?) {
    val artifact = when (version) {
      null -> {
        val testVersion = knownArtifactVersions[artifactId] ?: throw DependencyManagementException("Can't find dependency.")
        Artifact(artifactId, testVersion)
      }
      else -> Artifact(artifactId, version)
    }
    dependenciesBySource.put(sourceContext, artifact)
  }

  override fun getVersionOfDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion? {
    return dependenciesBySource[sourceContext].firstOrNull { it.id == artifactId }?.version
  }

  /**
   * Sets an artifact avaialble to be found by {@link findLatestArtifact}.
   * The TestProjectSystem by default will fail for any findLatestArtifact call unless the artifact
   * has been set to be available. (i.e. The fake maven repo is initially empty)
   */
  fun setArtifactAvailableForSearching(artifactId: GoogleMavenArtifactId, version: GradleVersion) {
    knownArtifactVersions[artifactId] = TestDependencyVersion(version)
  }

  override fun getModuleTemplates(module: Module, targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    TODO("not implemented")
  }

  override fun addDependency(module: Module, dependency: String) {
    TODO("not implemented")
  }

  override fun mergeBuildFiles(dependencies: String, destinationContents: String, supportLibVersionFilter: String?): String {
    TODO("not implemented")
  }

}