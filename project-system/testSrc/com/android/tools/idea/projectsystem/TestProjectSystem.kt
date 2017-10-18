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
import com.google.common.collect.HashMultimap
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
class TestProjectSystem : AndroidProjectSystem, AndroidProjectSystemProvider {
  data class Artifact(val id: GoogleMavenArtifactId, val version: GoogleMavenArtifactVersion)

  data class TestDependencyVersion(override val mavenVersion: GradleVersion?) : GoogleMavenArtifactVersion

  private val dependenciesByModule: HashMultimap<Module, Artifact> = HashMultimap.create()

  override val id: String = "com.android.tools.idea.projectsystem.TestProjectSystem"

  override val projectSystem = this

  override fun isApplicable(): Boolean = true

  fun addDependency(artifactId: GoogleMavenArtifactId, module: Module, mavenVersion: GradleVersion) {
    dependenciesByModule.put(module, Artifact(artifactId, TestDependencyVersion(mavenVersion)))
  }

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    return object : AndroidModuleSystem {
      override fun addDependency(dependency: String) {
      }

      override fun getResolvedVersion(artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion? {
        return dependenciesByModule[module].firstOrNull { it.id == artifactId }?.version
      }

      override fun getDeclaredVersion(artifactId: GoogleMavenArtifactId): GoogleMavenArtifactVersion? {
        return dependenciesByModule[module].firstOrNull { it.id == artifactId }?.version
      }

      override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
        return emptyList()
      }

      override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
        return CapabilityNotSupported()
      }

      override fun getInstantRunSupport(): CapabilityStatus {
        return CapabilityNotSupported()
      }
    }
  }

  override fun getDefaultApkFile(): VirtualFile? {
    TODO("not implemented")
  }

  override fun getPathToAapt(): Path {
    TODO("not implemented")
  }

  override fun buildProject() {
    TODO("not implemented")
  }

  override fun allowsFileCreation(): Boolean {
    TODO("not implemented")
  }

  override fun upgradeProjectToSupportInstantRun(): Boolean {
    TODO("not implemented")
  }

  override fun mergeBuildFiles(dependencies: String, destinationContents: String, supportLibVersionFilter: String?): String {
    TODO("not implemented")
  }

  override fun getSyncManager(): ProjectSystemSyncManager {
    TODO("not implemented")
  }
}