/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ide.gradle.model.builder

import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.impl.GradlePluginModelImpl
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

class GradlePluginModelBuilder : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean {
    return modelName == GradlePluginModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any {
    return GradlePluginModelImpl(
      project.plugins.hasPlugin("androidx.navigation.safeargs"),
      project.plugins.hasPlugin("androidx.navigation.safeargs.kotlin"),
      project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") || project.plugins.hasPlugin("kotlin-multiplatform"),
    )
  }
}
