/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.configurables.PsPathRendererImpl
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.psTestWithContext
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [PsAndroidModuleAnalyzer].
 */
@RunsInEdt
class PsAndroidModuleAnalyzerTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testPromotionMessages() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithContext(preparedProject, disableAnalysis = true) {
      val mainModule = project.findModuleByName("mainModule") as PsAndroidModule
      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val messageCollection = analyzer.analyze(mainModule)

      val messages = messageCollection
        .filter {
          val dependencyName = (it.path as? PsLibraryDependencyNavigationPath)?.toString().orEmpty()
          dependencyName.startsWith("com.example.")
        }
        .map { it.text to it.description!! }
        .toSet()

      assertThat(messages, equalTo(setOf(
        "Gradle promoted library version from 0.9.1 to 1.0" to "in: releaseImplementation",
        "Gradle promoted library version from 0.6 to 1.0" to "in: freeImplementation",
        "Gradle promoted library version from 0.6 to 1.0" to "in: freeImplementation",
        "Gradle promoted library version from 0.9.1 to 1.0" to "in: releaseImplementation"
      )))
    }
  }

  @Test
  fun testMessagesForBom() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_BOM)
    projectRule.psTestWithContext(preparedProject, disableAnalysis = true) {
      val mainModule = project.findModuleByName("app") as PsAndroidModule
      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val messageCollection = analyzer.analyze(mainModule)

      val messages = messageCollection
        .filter {
          val dependencyName = (it.path as? PsLibraryDependencyNavigationPath)?.toString().orEmpty()
          dependencyName.startsWith("com.android.support")
        }
        .map { it.text to it.description!! }
        .toSet()

      assertThat(messages, equalTo(setOf(
        "Gradle promoted library version to 28.0.0" to "in: implementation",
      )))
    }
  }
}
