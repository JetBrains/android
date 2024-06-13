/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.runsGradle

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.AndroidGradleClassJarProvider
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AndroidGradleClassJarProviderTest {
  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  // Regression test for b/144018886 checking that runtime aar gradle dependencies are added to the  returned classpath by
  // AndroidGradleClassJarProvider
  @Test
  fun testRuntimeDependencies() {
    gradleProjectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    // We use firebase-common because it includes a number of runtime aar dependencies that help us testing that they are correctly
    // included in the returned classpath.
    val mockitoDependency = Dependency.parse("com.google.firebase:firebase-common:12.0.1")
    val module = gradleProjectRule.project.findAppModule()

    val dependencyManager = GradleDependencyManager.getInstance(gradleProjectRule.project)
    assertTrue(dependencyManager.addDependenciesAndSync(module, listOf(mockitoDependency)))

    fun IdeDependencies.all() = this.libraries.flatMap {
      when (it) {
        is IdeAndroidLibrary -> it.runtimeJarFiles
        is IdeJavaLibrary -> listOf(it.artifact)
        else -> emptyList()
      }
    }

    val model = GradleAndroidModel.get(module)!!
    val runtimeDependencies = model.mainArtifact.runtimeClasspath.all().toSet() - model.mainArtifact.compileClasspath.all().toSet()
    assertTrue(runtimeDependencies.isNotEmpty())

    val classJarProvider = AndroidGradleClassJarProvider()
    assertTrue(classJarProvider.getModuleExternalLibraries(module).containsAll(runtimeDependencies))
  }
}