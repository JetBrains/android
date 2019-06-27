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

package com.android.java.model.builder

import com.android.java.model.ArtifactModel
import com.android.java.model.impl.ArtifactModelImpl
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File

/**
 * Builder for ArtifactModel.
 */
class ArtifactModelBuilder : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean {
    return modelName == ArtifactModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any? {
    val plugins = project.plugins

    // If there is no java or android plugin applied, it maybe a Jar/Aar module.
    // This is based on best guess, since Jar/Aar module doesn't require any plugin.
    val knownPlugins = arrayOf("java", "java-library", "com.android.application", "com.android.library", "android", "android-library",
                               "com.android.atom", "com.android.instantapp", "com.android.test", "com.android.model.atom",
                               "com.android.model.application", "com.android.model.library", "com.android.model.native",
                               "com.android.feature", "com.android.dynamic-feature")
    if (knownPlugins.any { plugins.hasPlugin(it) }) {
      return null
    }

    return ArtifactModelImpl(project.name.intern(),
                             getArtifactsByConfiguration(project))
  }

  private fun getArtifactsByConfiguration(project: Project): Map<String, Set<File>> {
    return project.configurations.associateBy({it.name}, {it.allArtifacts.files.files})
  }
}