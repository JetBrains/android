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
package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GradleVersion
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashMap

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
class TestProjectSystem() : AndroidProjectSystem, AndroidProjectSystemProvider {
  private val dependenciesMap: MutableMap<VirtualFile, MutableList<GoogleMavenArtifact>> = HashMap()
  private val artifactVersionMap: MutableMap<GoogleMavenArtifactId, TestDependencyVersion> = HashMap()

  override val id: String = "com.android.tools.idea.projectsystem.TestProjectSystem"

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

  override fun findArtifact(artifactId: GoogleMavenArtifactId): GoogleMavenArtifact? {
    val testVersion = artifactVersionMap[artifactId] ?: return null
    return GoogleMavenArtifact(artifactId, testVersion)
  }

  override fun addDependency(sourceContext: VirtualFile, artifact: GoogleMavenArtifact) {
    if (!dependenciesMap.containsKey(sourceContext)) {
      dependenciesMap[sourceContext] = ArrayList()
    }
    dependenciesMap[sourceContext]?.add(artifact)
  }

  override fun getDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): GoogleMavenArtifact? {
    return dependenciesMap[sourceContext]?.firstOrNull { it.artifactId == artifactId }
  }

  /**
   * Makes a dependency become available with the provided version.
   */
  fun addStubAvailableArtifact(artifactId: GoogleMavenArtifactId, version: GradleVersion) {
    artifactVersionMap[artifactId] = TestDependencyVersion(version)
  }

  /**
   * Adds a dependency to the source context's list of dependencies.  This does not add the dependency to the
   * list of available dependencies.
   */
  fun addStubDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId, version: GradleVersion) {
    addDependency(sourceContext, GoogleMavenArtifact(artifactId, TestDependencyVersion(version)))
  }

  /**
   * Checked the stub dependency records for added dependencies.
   */
  fun hasStubDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): Boolean {
    return dependenciesMap[sourceContext]?.firstOrNull { it.artifactId == artifactId } != null
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