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

import com.android.kotlin.multiplatform.ide.models.serialization.androidCompilationKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinProjectArtifactDependencyResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.findLibraryDependencyNode
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver.Context
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File

class KotlinMppAndroidProjectResolverExtension: KotlinMppGradleProjectResolverExtension {
  private val sourceSetResolver = KotlinMppAndroidSourceSetResolver()

  override fun provideAdditionalProjectArtifactDependencyResolvers(): List<KotlinProjectArtifactDependencyResolver> {
    return listOf(
      KotlinAndroidProjectArtifactDependencyResolver(sourceSetResolver)
    )
  }

  override fun afterMppGradleSourceSetDataNodeCreated(context: Context,
                                                      component: KotlinComponent,
                                                      sourceSetDataNode: DataNode<GradleSourceSetData>) {
    val kotlinCompilation = (component as? KotlinCompilation) ?: return
    val androidCompilation = kotlinCompilation.extras[androidCompilationKey]?.invoke() ?: return

    if (androidCompilation.hasMainDslInfo()) {
      sourceSetResolver.recordMainSourceSetForProject(
        projectPath = context.mppModel.targets.firstNotNullOf { it.extras[androidTargetKey] }.invoke()!!.projectPath,
        sourceSetName = androidCompilation.defaultSourceSetName
      )
    }
  }

  override fun afterPopulateSourceSetDependencies(context: Context,
                                                  sourceSetDataNode: DataNode<GradleSourceSetData>,
                                                  sourceSet: KotlinSourceSet,
                                                  dependencies: Set<IdeaKotlinDependency>,
                                                  dependencyNodes: List<DataNode<out AbstractDependencyData<*>>>) {
    dependencies.filterIsInstance<IdeaKotlinBinaryDependency>().forEach { ideaKotlinDependency ->
      val androidLibInfo = ideaKotlinDependency.extras[androidDependencyKey] ?: return@forEach

      // This is a dependency on an android library, add the fine-grained information about the contents of the library to the dependency
      // node.
      if (!androidLibInfo.hasLibrary() && !androidLibInfo.library.hasAndroidLibraryData()) {
        return@forEach
      }
      val node = sourceSetDataNode.findLibraryDependencyNode(ideaKotlinDependency) ?: return@forEach

      fun String.toFile(): File? {
        return File(this).takeIf { it.exists() }
      }

      // Discard what the kotlin IDE plugin has added.
      node.data.target.forgetAllPaths()

      androidLibInfo.library.androidLibraryData.takeIf { it.hasManifest() }?.manifest?.absolutePath?.toFile()?.let {
        node.data.target.addPath(LibraryPathType.BINARY, it.path)
      }

      androidLibInfo.library.androidLibraryData.takeIf { it.hasResFolder() }?.resFolder?.absolutePath?.toFile()?.let {
        node.data.target.addPath(LibraryPathType.BINARY, it.path)
      }

      androidLibInfo.library.androidLibraryData.compileJarFilesList.mapNotNull {it.absolutePath.toFile() }.forEach {
        node.data.target.addPath(LibraryPathType.BINARY, it.path)
      }
    }
  }
}
