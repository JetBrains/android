/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.settings.BuildSystemName
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Bazel implementation of [DesugaringLibraryConfigFilesLocator].
 */
class BazelDesugaringLibraryConfigFilesLocator : DesugaringLibraryConfigFilesLocator {

  override fun getDesugarLibraryConfigFilesKnown(): Boolean {
    return true
  }

  override fun getDesugarLibraryConfigFiles(project: Project): ImmutableList<Path> {
    val workspaceRoot = WorkspaceRoot.fromProjectSafe(project)?.path() ?: return ImmutableList.of()
    val configFile = workspaceRoot.resolve("bazel-bin/external/rules_android+/tools/android/full_desugar_jdk_libs_config.json")
    return if (configFile.exists()) {
      ImmutableList.of(configFile)
    } else {
      ImmutableList.of()
    }
  }

  override fun buildSystem(): BuildSystemName {
    return BuildSystemName.Bazel
  }
}
