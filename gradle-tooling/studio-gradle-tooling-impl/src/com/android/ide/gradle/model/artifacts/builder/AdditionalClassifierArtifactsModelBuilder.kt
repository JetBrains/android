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
import com.android.ide.gradle.model.artifacts.impl.AdditionalClassifierArtifactsImpl
import com.android.ide.gradle.model.artifacts.impl.AdditionalClassifierArtifactsModelImpl
import com.android.ide.gradle.model.artifacts.impl.ModuleIdentifierImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.DocsType
import org.gradle.api.component.Artifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.util.GradleVersion
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

  // DocsType attribute was added in 5.6, so require that version to use artifact view based fetching.
  private val useArtifactViews = GradleVersion.current() >= GradleVersion.version("5.6")

  override fun buildAll(modelName: String, parameter: AdditionalClassifierArtifactsModelParameter, project: Project): Any {
    if (parameter.artifactIdentifiers.isEmpty()) {
      return AdditionalClassifierArtifactsModelImpl(emptyList(), null)
    }

    try {
      val artifactsCollector = ArtifactsCollector()
      val componentsIds = getComponentIds(parameter)
      getPomFiles(componentsIds, project) { id, file -> artifactsCollector.setPom(id, file) }
      if (useArtifactViews) {
        project.dependencies.attributesSchema.attribute(DocsType.DOCS_TYPE_ATTRIBUTE) {
          it.compatibilityRules.add(SourcesCompatibilityRule::class.java)
        }
        getArtifacts(project, DocsType.JAVADOC, parameter.artifactIdentifiers) { id, file -> artifactsCollector.setJavadoc(id, file) }
        getArtifacts(project, DocsType.SOURCES, parameter.artifactIdentifiers) { id, file -> artifactsCollector.addSources(id, file) }
        // Find the libraries which has pom file but is missing source or javadoc. This could happen when Gradle metadata doesn't include
        // source/javadoc. In this case, use pom to obtain source/javadoc.
        val missingJavadoc = mutableListOf<DefaultModuleComponentIdentifier>()
        val missingSources = mutableListOf<DefaultModuleComponentIdentifier>()
        componentsIds.map { componentId ->
          val artifactId = ArtifactIdentifierImpl(componentId.group, componentId.module, componentId.version)
          artifactsCollector.getAdditionalPaths(artifactId)?.let {
            if (it.javaDoc == null) { missingJavadoc.add(componentId) }
            if (it.sources.isEmpty()) { missingSources.add(componentId) }
          }
        }
        getArtifactsWithArtifactResolutionQuery(missingJavadoc, project, artifactsCollector, withJavadoc = true, withSources =  false)
        getArtifactsWithArtifactResolutionQuery(missingSources, project, artifactsCollector, withJavadoc = false, withSources =  true)
      } else {
       getArtifactsWithArtifactResolutionQuery(componentsIds, project, artifactsCollector, withJavadoc = true, withSources = true)
      }

      val artifacts = parameter.artifactIdentifiers.map {
        val artifactId = ArtifactIdentifierImpl(it.groupId, it.artifactId, it.version)
        val paths = artifactsCollector.getAdditionalPaths(artifactId)
        AdditionalClassifierArtifactsImpl(
          id = artifactId,
          javadoc = paths?.javaDoc,
          sources = paths?.sources ?: emptyList(),
          mavenPom = paths?.pomFile,
        )
      }
      return AdditionalClassifierArtifactsModelImpl(artifacts, null)
    }
    catch (t: LinkageError) { // This must be safe to run against all versions of Gradle, so fail hard if we hit that issue.
      throw t
    }
    catch (t: Throwable) {
      return AdditionalClassifierArtifactsModelImpl(emptyList(), "Unable to download sources/javadoc: " + t.message)
    }
  }

  private fun getPomFiles(componentsIds: List<DefaultModuleComponentIdentifier>,
                          project: Project,
                          collector: (ArtifactIdentifierImpl, File) -> Unit) {
    val pomQuery = project.dependencies.createArtifactResolutionQuery()
      .forComponents(componentsIds)
      .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)

    pomQuery.execute().resolvedComponents.forEach {
      val id = it.id as ModuleComponentIdentifier
      val pomFile = getFile(it, MavenPomArtifact::class.java) ?: return@forEach
      collector(ArtifactIdentifierImpl(id.group, id.module, id.version), pomFile)
    }
  }

  private fun getComponentIds(parameter: AdditionalClassifierArtifactsModelParameter) =
    parameter.artifactIdentifiers.map {
      DefaultModuleComponentIdentifier(ModuleIdentifierImpl(it.groupId, it.artifactId), it.version)
    }

  private fun getArtifacts(project: Project,
                           docsType: String,
                           artifactIdentifiers: Collection<ArtifactIdentifier>,
                           collector: (ArtifactIdentifierImpl, File) -> Unit) {
    val resolvableConfiguration = project.configurations.detachedConfiguration().also {
      it.attributes.run<AttributeContainer, Unit> {
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.DOCUMENTATION))
        attribute(Attribute.of("artifactType", String::class.java), ArtifactTypeDefinition.JAR_TYPE)
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.objects.named(DocsType::class.java, docsType))
      }
    }
    artifactIdentifiers.asDependencies(project).forEach {
      resolvableConfiguration.dependencies.add(it)
    }
    resolvableConfiguration.incoming.artifactView { view ->
      view.lenient(true)
      view.componentFilter { it is ModuleComponentIdentifier }
    }.artifacts.forEach {
      val id = it.id.componentIdentifier as ModuleComponentIdentifier
      val identifier = ArtifactIdentifierImpl(id.group, id.module, id.version)

      collector(identifier, it.file)
    }
  }

  private fun Collection<ArtifactIdentifier>.asDependencies(project: Project): Sequence<Dependency> {
    return asSequence().map { project.dependencies.create(it.groupId + ":" + it.artifactId + ":" + it.version) }
  }

  /**
   * Use this with older Gradle versions, when [useArtifactViews] is false.
   */
  private fun getArtifactsWithArtifactResolutionQuery(
    componentsIds: List<DefaultModuleComponentIdentifier>,
    project: Project,
    collector: ArtifactsCollector,
    withJavadoc: Boolean,
    withSources: Boolean,
  ) {
    val artifactTypes: MutableList<Class<out Artifact>> = mutableListOf()
    if (withJavadoc) artifactTypes.add(JavadocArtifact::class.java)
    if (withSources) artifactTypes.add(SourcesArtifact::class.java)

    val docQuery = project.dependencies.createArtifactResolutionQuery()
      .forComponents(componentsIds)
      .withArtifacts(JvmLibrary::class.java, artifactTypes)

    docQuery.execute().resolvedComponents.filter { it.id is ModuleComponentIdentifier }.forEach {
      val id = it.id as ModuleComponentIdentifier
      val artifactId = ArtifactIdentifierImpl(id.group, id.module, id.version)

      if (withJavadoc) { getFile(it, JavadocArtifact::class.java)?.let { collector.setJavadoc(artifactId, it) } }
      if (withSources) { getFile(it, SourcesArtifact::class.java)?.let { collector.addSources(artifactId, it) } }
    }
  }

  private fun getFile(result: ComponentArtifactsResult, clazz: Class<out Artifact>): File? {
    return result.getArtifacts(clazz)
      .filterIsInstance(ResolvedArtifactResult::class.java).firstOrNull()
      ?.file
  }

  private class ArtifactsCollector {
    private val index = mutableMapOf<ArtifactIdentifierImpl, ArtifactsPaths>()

    fun setJavadoc(id: ArtifactIdentifierImpl, file: File) {
      val paths = index[id] ?: ArtifactsPaths()
      paths.javaDoc = file
      index[id] = paths
    }

    fun addSources(id: ArtifactIdentifierImpl, file: File) {
      val paths = index[id] ?: ArtifactsPaths()
      paths.sources.add(file)
      index[id] = paths
    }

    fun setPom(id: ArtifactIdentifierImpl, file: File) {
      val paths = index[id] ?: ArtifactsPaths()
      paths.pomFile = file
      index[id] = paths
    }

    fun getAdditionalPaths(id: ArtifactIdentifierImpl): ArtifactsPaths? = index[id]
  }

  private class ArtifactsPaths {
    var pomFile: File? = null
    var javaDoc: File? = null
    val sources: MutableList<File> = mutableListOf()
  }
}

/**
 * Because of http://b/272214715, some AndroidX KMP libraries have their sources published as
 * `"org.gradle.docstype=fake-sources"`. Except for this attribute, these are valid source variants,
 * and we should fetch them.
 */
class SourcesCompatibilityRule : AttributeCompatibilityRule<DocsType> {
  override fun execute(details: CompatibilityCheckDetails<DocsType>) {
    val producer = details.producerValue?.name
    val consumer = details.consumerValue?.name
    if (producer == consumer) {
      details.compatible()
    } else if (consumer == DocsType.SOURCES && producer == "fake-sources") {
      details.compatible()
    } else {
      details.incompatible()
    }
  }
}