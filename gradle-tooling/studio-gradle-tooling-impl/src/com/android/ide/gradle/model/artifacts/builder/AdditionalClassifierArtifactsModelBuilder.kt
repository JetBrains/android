/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.gradle.model.artifacts.builder

import com.android.ide.gradle.model.AdditionalClassifierArtifactsModelParameter
import com.android.ide.gradle.model.ArtifactIdentifierImpl
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel.SAMPLE_SOURCE_CLASSIFIER
import com.android.ide.gradle.model.artifacts.impl.AdditionalClassifierArtifactsImpl
import com.android.ide.gradle.model.artifacts.impl.AdditionalClassifierArtifactsModelImpl
import com.android.ide.gradle.model.artifacts.samples.SamplesVariantRule
import com.android.ide.gradle.model.builder.isGradleAtLeast
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.DocsType
import org.gradle.api.component.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.File


/**
 * Model Builder for [AdditionalClassifierArtifactsModel].
 *
 * This model builder downloads sources and javadoc for components specifies in parameter, and returns model
 * [AdditionalClassifierArtifactsModel], which contains the locations of downloaded jar files.
 */
class AdditionalClassifierArtifactsModelBuilder : ParameterizedToolingModelBuilder<AdditionalClassifierArtifactsModelParameter> {
  override fun canBuild(modelName: String): Boolean {
    return modelName == AdditionalClassifierArtifactsModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any? {
    throw RuntimeException("Please use parameterized tooling API to obtain AdditionalArtifactsModelBuilder model.")
  }

  override fun getParameterType(): Class<AdditionalClassifierArtifactsModelParameter> {
    return AdditionalClassifierArtifactsModelParameter::class.java
  }

  override fun buildAll(modelName: String, parameter: AdditionalClassifierArtifactsModelParameter, project: Project): Any {
    // SamplesVariantRule requires Gradle 6.0+ because it uses VariantMetadata.withFiles.
    if (isGradleAtLeast(project.gradle.gradleVersion, "6.0")) {
      project.dependencies.components.all(SamplesVariantRule::class.java)
    }

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

    var artifacts = emptyList<AdditionalClassifierArtifacts>()
    var message: String? = null

    if (ids.isEmpty()) {
      return AdditionalClassifierArtifactsModelImpl(artifacts, message = null)
    }

    try {
      // Create query for Maven Pom File.
      val pomQuery = project.dependencies.createArtifactResolutionQuery()
        .forComponents(ids)
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)

      // Map from component id to Pom File.
      val idToPomFile = pomQuery.execute().resolvedComponents.map {
        it.id.displayName to getFile(it, MavenPomArtifact::class.java)
      }.toMap()

      // Create map from component id to location of sample sources file.
      val idToSampleLocation: Map<String, File?> =
        if (parameter.downloadAndroidxUISamplesSources) {
          getSampleSources(parameter, project)
        }
        else {
          emptyMap()
        }

      // Create query for Javadoc and Sources.
      val docQuery = project.dependencies.createArtifactResolutionQuery()
        .forComponents(ids)
        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java, JavadocArtifact::class.java)

      artifacts = docQuery.execute().resolvedComponents.filter { it.id is ModuleComponentIdentifier }.map {
        val id = it.id as ModuleComponentIdentifier
        AdditionalClassifierArtifactsImpl(
          ArtifactIdentifierImpl(id.group, id.module, id.version),
          getFile(it, SourcesArtifact::class.java),
          getFile(it, JavadocArtifact::class.java),
          idToPomFile[it.id.displayName],
          idToSampleLocation[it.id.displayName]
        )
      }
    }
    catch (t: Throwable) {
      message = "Unable to download sources/javadoc: " + t.message
    }
    return AdditionalClassifierArtifactsModelImpl(artifacts, message)
  }

  private fun getSampleSources(parameter: AdditionalClassifierArtifactsModelParameter, project: Project): Map<String, File?> {
    val detachedConfiguration = project.configurations.detachedConfiguration()
    parameter.artifactIdentifiers
      // Only androidx.ui and androidx.compose use the @Sampled annotation as of today (January 2020).
      .filter { it.groupId.startsWith("androidx.ui") || it.groupId.startsWith("androidx.compose") }
      .forEach {
        val dependency = project.dependencies.create(it.groupId + ":" + it.artifactId + ":" + it.version)
        detachedConfiguration.dependencies.add(dependency)
      }
    detachedConfiguration.attributes.attribute(
      DocsType.DOCS_TYPE_ATTRIBUTE,
      project.objects.named(DocsType::class.java, SAMPLE_SOURCE_CLASSIFIER))

    val samples = mutableMapOf<String, File?>()

    detachedConfiguration.incoming.artifactView {
      it.lenient(true) // this will make it not fail if something does not have samples
    }.artifacts.forEach {
      val id = it.id.componentIdentifier.displayName
      samples[id] = it.file
    }
    return samples
  }

  private fun getFile(result: ComponentArtifactsResult, clazz: Class<out Artifact>): File? {
    return result.getArtifacts(clazz)
      .filterIsInstance(ResolvedArtifactResult::class.java).firstOrNull()
      ?.file
  }
}