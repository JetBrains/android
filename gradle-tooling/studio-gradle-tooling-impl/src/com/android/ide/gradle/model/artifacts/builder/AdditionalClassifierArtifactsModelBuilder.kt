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
import com.android.ide.gradle.model.ArtifactIdentifier
import com.android.ide.gradle.model.ArtifactIdentifierImpl
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel.SAMPLE_SOURCE_CLASSIFIER
import com.android.ide.gradle.model.artifacts.impl.AdditionalClassifierArtifactsImpl
import com.android.ide.gradle.model.artifacts.impl.AdditionalClassifierArtifactsModelImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.component.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
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
    if (parameter.artifactIdentifiers.isEmpty()) {
      return AdditionalClassifierArtifactsModelImpl(emptyList(), null)
    }

    try {
      val idToPomFile = getPomFiles(parameter, project)
      val idToJavadoc = getArtifacts(project, DocsType.JAVADOC, parameter.artifactIdentifiers)
      val idToSources = getArtifacts(project, DocsType.SOURCES, parameter.artifactIdentifiers)
      val idToSampleLocation = getSampleSources(parameter, project)

      val artifacts = parameter.artifactIdentifiers.map {
        val artifactId = ArtifactIdentifierImpl(it.groupId, it.artifactId, it.version)
        AdditionalClassifierArtifactsImpl(
          id = artifactId,
          javadoc = idToJavadoc[artifactId],
          sources = idToSources[artifactId],
          mavenPom = idToPomFile[artifactId],
          sampleSources = idToSampleLocation[artifactId],
        )
      }
      return AdditionalClassifierArtifactsModelImpl(artifacts, null)
    }
    catch (t: Throwable) {
      return AdditionalClassifierArtifactsModelImpl(emptyList(), "Unable to download sources/javadoc: " + t.message)
    }
  }

  private fun getPomFiles(
    parameter: AdditionalClassifierArtifactsModelParameter,
    project: Project
  ): Map<ArtifactIdentifierImpl, File?> {
    // Create query for Maven Pom File.
    val ids = parameter.artifactIdentifiers.map {
      DefaultModuleComponentIdentifier(
        object : ModuleIdentifier {
          override fun getGroup() = it.groupId
          override fun getName() = it.artifactId
        }, it.version
      )
    }

    val pomQuery = project.dependencies.createArtifactResolutionQuery()
      .forComponents(ids)
      .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)

    fun getFile(result: ComponentArtifactsResult, clazz: Class<out Artifact>): File? {
      return result.getArtifacts(clazz)
        .filterIsInstance(ResolvedArtifactResult::class.java).firstOrNull()
        ?.file
    }

    // Map from component id to Pom File.
    return pomQuery.execute().resolvedComponents.associate {
      val id = it.id as ModuleComponentIdentifier
      ArtifactIdentifierImpl(id.group, id.module, id.version) to getFile(it, MavenPomArtifact::class.java)
    }
  }

  private fun getArtifacts(project: Project,
                           docsType: String,
                           artifactIdentifiers: Collection<ArtifactIdentifier>): Map<ArtifactIdentifierImpl, File> {
    val resolvableConfiguration = project.configurations.detachedConfiguration().also {
      it.attributes.run<AttributeContainer, Unit> {
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.DOCUMENTATION))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.objects.named(DocsType::class.java, docsType))
      }
    }
    artifactIdentifiers.asDependencies(project).forEach {
      resolvableConfiguration.dependencies.add(it)
    }
    return resolvableConfiguration.incoming.artifactView { view ->
      view.lenient(true)
      view.componentFilter { it is ModuleComponentIdentifier }
    }.artifacts.associate {
      val id = it.id.componentIdentifier as ModuleComponentIdentifier
      ArtifactIdentifierImpl(id.group, id.module, id.version) to it.file
    }
  }

  private fun getSampleSources(parameter: AdditionalClassifierArtifactsModelParameter, project: Project): Map<ArtifactIdentifierImpl, File> {
    if (!parameter.downloadAndroidxUISamplesSources) return emptyMap()

    // Only androidx.ui and androidx.compose use the @Sampled annotation as of today (January 2020).
    val artifactsWithSamples = parameter.artifactIdentifiers
      .filter { it.groupId.startsWith("androidx.ui") || it.groupId.startsWith("androidx.compose") }

    return getArtifacts(project, SAMPLE_SOURCE_CLASSIFIER, artifactsWithSamples)
  }

  private fun Collection<ArtifactIdentifier>.asDependencies(project: Project): Sequence<Dependency> {
    return asSequence().map { project.dependencies.create(it.groupId + ":" + it.artifactId + ":" + it.version) }
  }
}