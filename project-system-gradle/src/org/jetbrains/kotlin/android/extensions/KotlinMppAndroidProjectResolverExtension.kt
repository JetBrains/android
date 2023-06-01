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
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import com.intellij.openapi.externalSystem.model.DataNode
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinProjectArtifactDependencyResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver.Context
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

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
}
