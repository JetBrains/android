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
package com.android.ide.gradle.model.sources.builder

import com.android.ide.gradle.model.sources.SourcesAndJavadocArtifact
import com.android.ide.gradle.model.sources.SourcesAndJavadocArtifacts
import com.android.ide.gradle.model.sources.SourcesAndJavadocParameter
import com.android.ide.gradle.model.sources.impl.SourcesAndJavadocArtifactIdentifierImpl
import com.android.ide.gradle.model.sources.impl.SourcesAndJavadocArtifactImpl
import com.android.ide.gradle.model.sources.impl.SourcesAndJavadocArtifactsImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.component.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.File

/**
 * Model Builder for [SourcesAndJavadocArtifacts].
 *
 * This model builder downloads sources and javadoc for components specifies in parameter, and returns model
 * [SourcesAndJavadocArtifacts], which contains the locations of downloaded jar files.
 */
class SourcesAndJavadocModelBuilder : ParameterizedToolingModelBuilder<SourcesAndJavadocParameter> {
  override fun canBuild(modelName: String): Boolean {
    return modelName == SourcesAndJavadocArtifacts::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any? {
    throw RuntimeException("Please use parameterized tooling API to obtain SourcesAndJavadocArtifacts model.")
  }

  override fun getParameterType(): Class<SourcesAndJavadocParameter> {
    return SourcesAndJavadocParameter::class.java
  }

  override fun buildAll(modelName: String, parameter: SourcesAndJavadocParameter, project: Project): Any {
    // Collect the components to download Sources and Javadoc for. DefaultModuleComponentIdentifier is the only supported type.
    // See DefaultArtifactResolutionQuery::validateComponentIdentifier.
    val ids = parameter.artifactIdentifiers.map {
      DefaultModuleComponentIdentifier(
        object : ModuleIdentifier {
          override fun getGroup() = it.groupId
          override fun getName() = it.artifactId
        }, it.version
      )
    }

    var artifacts = emptyList<SourcesAndJavadocArtifact>()
    var message: String? = null

    if (ids.isEmpty()) {
      return SourcesAndJavadocArtifactsImpl(artifacts, message)
    }

    try {
      val query = project.dependencies.createArtifactResolutionQuery()
        .forComponents(ids)
        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java, JavadocArtifact::class.java)

      artifacts = query.execute().resolvedComponents.filter { it.id is ModuleComponentIdentifier }.map {
        val id = it.id as ModuleComponentIdentifier
        SourcesAndJavadocArtifactImpl(
          SourcesAndJavadocArtifactIdentifierImpl(id.group, id.module, id.version),
          getFile(it, SourcesArtifact::class.java),
          getFile(it, JavadocArtifact::class.java))
      }
    }
    catch (t: Throwable) {
      message = "Unable to download sources/javadoc: " + t.message
    }
    return SourcesAndJavadocArtifactsImpl(artifacts, message)
  }

  private fun getFile(result: ComponentArtifactsResult, clazz: Class<out Artifact>): File? {
    return result.getArtifacts(clazz)
      .filterIsInstance(ResolvedArtifactResult::class.java).firstOrNull()
      ?.file
  }
}