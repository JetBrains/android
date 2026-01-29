/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AndroidLibraryDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.ModuleModelBuilder
import com.android.tools.idea.testing.buildAndroidProjectStub
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LambdaResolutionGradleTokenTest {
  private val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule val rule = RuleChain(projectRule, EdtRule())

  @Test
  fun testLambdaResolutionMessageWithOldKotlinVersion() {
    projectRule.setupProjectFrom(*with(kotlin = "1.9.10"))
    assertThat(findCauseOfMissingSourceLocation()).isNull()
  }

  @Test
  fun testLambdaResolutionMessageWithOldComposeLayoutInspectorAgent() {
    projectRule.setupProjectFrom(*with(compose = "1.8.3"))
    val problem = findCauseOfMissingSourceLocation()!!
    assertThat(problem.type).isEqualTo(ProblemType.COMPOSE_UI)
    assertThat(problem.getMessage()).isEqualTo("The androidx.compose.ui:ui library version should be at least: 1.9.0, current version: 1.8.3")
  }

  @Test
  fun testLambdaResolutionMessageWithOldAgp() {
    projectRule.setupProjectFrom(*with(agp = "8.13.0"))
    val problem = findCauseOfMissingSourceLocation()!!
    assertThat(problem.type).isEqualTo(ProblemType.D8)
    assertThat(problem.getMessage()).isEqualTo("AGP should be at least version: 9.0.0, current version: 8.13.0")
  }

  @Test
  fun testLambdaResolutionMessageWithJava11() {
    projectRule.setupProjectFrom(*with(java = "11"))
    val problem = findCauseOfMissingSourceLocation()!!
    assertThat(problem.type).isEqualTo(ProblemType.JDK)
    assertThat(problem.getMessage()).isEqualTo("The sourceCompatibility should be at least JDK 17, current level is: JDK 11")
  }

  private fun findCauseOfMissingSourceLocation(): VersionProblem? {
    val projectSystem = GradleProjectSystem(projectRule.project)
    val facet = projectRule.project.getAndroidFacets().first()
    val token = LambdaResolutionGradleToken()
    return token.findCauseOfMissingSourceLocation(projectSystem, facet.module)
  }

  private fun with(
    kotlin: String = "2.2.20",
    compose: String = "1.9.0",
    agp: String = "9.0.0",
    java: String = "17",
  ): Array<ModuleModelBuilder> {
    val compileOptions =
      IdeJavaCompileOptionsImpl(
        encoding = "encoding",
        sourceCompatibility = java,
        targetCompatibility = "targetCompatibility",
        isCoreLibraryDesugaringEnabled = false,
      )

    val builder =
      AndroidModuleModelBuilder(
        agpVersion = agp,
        gradlePath = ":mylib",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            androidProject = { buildAndroidProjectStub().copy(javaCompileOptions = compileOptions) },
            androidLibraryDependencyList = {
              listOf(
                AndroidLibraryDependency.fromAddress("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin"),
                AndroidLibraryDependency.fromAddress("androidx.compose.ui:ui-android:$compose"),
              )
            },
          ),
      )
    return arrayOf(JavaModuleModelBuilder.rootModuleBuilder, builder)
  }
}
