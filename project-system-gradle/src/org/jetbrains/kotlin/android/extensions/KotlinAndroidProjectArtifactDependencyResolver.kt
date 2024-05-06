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
package org.jetbrains.kotlin.android.extensions

import com.android.builder.model.proto.ide.ProjectInfo
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinProjectArtifactDependencyResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinProjectModuleId
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver

/**
 * A class that is used to resolve dependencies from android sourceSets in a kotlin multiplatform projects to android projects, as the
 * kotlin IDE plugin doesn't know how to resolve these.
 */
internal class KotlinAndroidProjectArtifactDependencyResolver(
  private val sourceSetResolver: KotlinMppAndroidSourceSetResolver
): KotlinProjectArtifactDependencyResolver {
  private val logger = Logger.getInstance(this::class.java)

  private fun ProjectInfo.isAndroidComponent(): Boolean =
    componentInfo.attributesMap.containsKey("com.android.build.api.attributes.AgpVersionAttr")

  private fun ProjectInfo.isKmpAndroidComponent(): Boolean =
    componentInfo.attributesMap["org.jetbrains.kotlin.platform.type"] == "jvm" &&
    componentInfo.attributesMap[TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE.name] == TargetJvmEnvironment.ANDROID


  override fun resolve(context: KotlinMppGradleProjectResolver.Context,
                       sourceSetNode: DataNode<GradleSourceSetData>,
                       dependency: IdeaKotlinProjectArtifactDependency): Set<IdeaKotlinSourceDependency> {
    val dependencyInfo = dependency.extras[androidDependencyKey] ?: return emptySet()

    if (!dependencyInfo.hasLibrary() || !dependencyInfo.library.hasProjectInfo()) {
      return emptySet()
    }

    // This is a dependency on another kotlin multiplatform project and on the android target of it. Get the main sourceSet of that project,
    // expand it to include the transitive sourceSets and return them.
    if (dependencyInfo.library.projectInfo.isKmpAndroidComponent()) {
      return sourceSetResolver.getMainSourceSetForProject(dependencyInfo.library.projectInfo.projectPath)?.let { mainSourceSet ->
        resolveDependencyOnKmpAndroid(
          context.projectDataNode, dependency, mainSourceSet
        )
      } ?: run {
        logger.error("Unable to find the main android sourceSet for the kotlin multiplatform module " +
                     "${dependencyInfo.library.projectInfo.projectPath}.")
        emptySet()
      }
    }

    // This is a dependency on an android project but not a kotlin multiplatform one, check if it's a dependency on testFixtures or main.
    val androidSourceSets = when {
      dependencyInfo.library.projectInfo.isAndroidComponent() ->
        if (dependencyInfo.library.projectInfo.componentInfo.isTestFixtures) {
          setOf(IdeModuleWellKnownSourceSet.TEST_FIXTURES.sourceSetName)
        } else {
          setOf(IdeModuleWellKnownSourceSet.MAIN.sourceSetName)
        }
      else -> {
        logger.error("Expected a dependency on an android module, but instead found unknown module " +
                     "${dependencyInfo.library.projectInfo.projectPath}.")
        emptySet()
      }
    }

    return dependency.resolved(androidSourceSets)
  }

  private fun resolveDependencyOnKmpAndroid(
    projectNode: DataNode<ProjectData>,
    dependency: IdeaKotlinProjectArtifactDependency,
    mainSourceSet: String
  ): Set<IdeaKotlinSourceDependency> {
    val sourceSetMap = projectNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS).orEmpty()
    val sourceSetDataNode = sourceSetMap[
      KotlinProjectModuleId(dependency.coordinates).plus(mainSourceSet).toString()
    ]?.first ?: return emptySet()

    val sourceSets = sourceSetDataNode.kotlinSourceSetData?.sourceSetInfo?.dependsOn.orEmpty().mapNotNull { dependsOnId ->
      sourceSetMap[dependsOnId]?.second?.name
    } + mainSourceSet

    return dependency.resolved(sourceSets)
  }
}