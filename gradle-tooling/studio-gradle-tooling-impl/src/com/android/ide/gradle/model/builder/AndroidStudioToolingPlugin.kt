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

import com.android.ide.gradle.model.artifacts.builder.AdditionalClassifierArtifactsModelBuilder
import com.android.ide.gradle.model.composites.BuildMapModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.GradleVersion
import javax.inject.Inject

class AndroidStudioToolingPlugin @Inject
internal constructor(private val registry: ToolingModelBuilderRegistry) : Plugin<Project> {

  override fun apply(project: Project) {
    registry.register(GradlePluginModelBuilder())
    registry.register(BuildMapModelBuilder())
    registry.register(LegacyV1AgpVersionModelBuilder())
    registry.register(GradlePropertiesModelBuilder())
    // NOTE: The minimum supported AGP version is 3.2 and it requires Gradle 4.6.
    // AdditionalArtifactsModelBuilder extends ParameterizedToolingModelBuilder, which is available since Gradle 4.4.
    if (GradleVersion.current() >= minGradleVersion) {
      LegacyAndroidGradlePluginPropertiesModelBuilder.maybeRegister(project, registry)
      val isJetBrains = "JetBrains" == System.getProperty("idea.vendor.name")
      val isDownloadSources = System.getProperty("idea.gradle.download.sources", "true").toBoolean()
      if ((isJetBrains && isDownloadSources) || !isJetBrains) {
        registry.register(AdditionalClassifierArtifactsModelBuilder())
      }
    }
  }
}

private val minGradleVersion = GradleVersion.version("4.4")