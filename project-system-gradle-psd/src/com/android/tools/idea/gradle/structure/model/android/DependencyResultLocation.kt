/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.dsl.api.GradleFileModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.intellij.openapi.vfs.VirtualFile

/**
 * Class encapsulates logic about result dependency location as it can be in different file that dependency declaration.
 * For example `implementation libs.core` pointing to default versions.toml file.
 */
class DependencyResultLocation(val model: ArtifactDependencyModel) {
  private val resultLocation: VirtualFile
    get() {
      val resultModel = model.completeModel().resultModel
      return resultModel.gradleFile
    }
  fun matchLocation(model: GradleFileModel?): Boolean = model?.virtualFile == resultLocation
}