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
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.*
import com.google.common.collect.HashMultimap
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
class TestProjectSystem(defaultProjectSystem: DefaultProjectSystem) : AndroidProjectSystem by defaultProjectSystem, AndroidProjectSystemProvider {
  data class Artifact(val id: GoogleMavenArtifactId, val version: GoogleMavenArtifactVersion)

  data class TestDependencyVersion(override val mavenVersion: GradleVersion?) : GoogleMavenArtifactVersion

  private val dependenciesBySource: HashMultimap<VirtualFile, Artifact> = HashMultimap.create()

  override val id: String = "com.android.tools.idea.common.projectsystem.TestProjectSystem"

  override val projectSystem = this

  override fun isApplicable(): Boolean = true

  override fun getResolvedVersion(artifactId: GoogleMavenArtifactId, sourceContext: VirtualFile): GoogleMavenArtifactVersion? {
    return dependenciesBySource[sourceContext].firstOrNull { it.id == artifactId }?.version
  }

  override fun getDeclaredVersion(artifactId: GoogleMavenArtifactId, sourceContext: VirtualFile): GoogleMavenArtifactVersion? {
    return dependenciesBySource[sourceContext].firstOrNull { it.id == artifactId }?.version
  }

  fun addDependency(artifactId: GoogleMavenArtifactId, sourceContext: VirtualFile, mavenVersion: GradleVersion) {
    dependenciesBySource.put(sourceContext, Artifact(artifactId, TestDependencyVersion(mavenVersion)))
  }
}