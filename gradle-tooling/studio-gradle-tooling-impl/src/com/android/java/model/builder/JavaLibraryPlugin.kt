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

import com.android.java.model.builder.JavaModelBuilder.Companion.isGradleAtLeast
import com.android.model.sources.builder.SourcesAndJavadocModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

/** Custom plugin for Java Library.  */
class JavaLibraryPlugin @Inject
internal constructor(private val registry: ToolingModelBuilderRegistry) : Plugin<Project> {

  override fun apply(project: Project) {
    registry.register(JavaModelBuilder())
    registry.register(ArtifactModelBuilder())
    registry.register(GradlePluginModelBuilder())
    // SourcesAndJavadocModelBuilder extends ParameterizedToolingModelBuilder, which is available since Gradle 4.4.
    if (isGradleAtLeast(project.gradle.gradleVersion, "4.4")) {
      registry.register(SourcesAndJavadocModelBuilder())
    }
  }
}
