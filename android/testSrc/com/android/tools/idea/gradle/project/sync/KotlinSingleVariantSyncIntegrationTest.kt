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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerExtension
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.KotlinGradleModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

@RunsInEdt
class KotlinSingleVariantSyncIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  @Test
  fun kotlinSingleVariantSync() {
    ApplicationManager.getApplication().registerExtension(
      @Suppress("UnstableApiUsage")
      GradleProjectResolverExtension.EP_NAME,
      KotlinSingleVariantSyncTestProjectResolverExtension(), // Note: a new instance is created by the external system.
      projectRule.fixture.testRootDisposable
    )
    prepareGradleProject(TestProjectPaths.KOTLIN_KAPT, "project")
    openPreparedProject("project") {
      // TODO(b/180112678): Fix when Kotlin single variant sync is implemented.
      assertThat(KotlinSingleVariantSyncTestProjectResolverExtension.kotlinSourceSets["app"].orEmpty()).containsExactly(
        "debugAndroidTest", "debug", "debugUnitTest"
      )
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData"
  override fun getAdditionalRepos(): Collection<File> = emptyList()
}

class KotlinSingleVariantSyncTestProjectResolverExtension : AbstractProjectResolverExtension() {
  companion object {
    val kotlinSourceSets = mutableMapOf<String, MutableSet<String>>()
  }

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val kotlinGradleModel = resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)
    if (kotlinGradleModel != null) {
      kotlinSourceSets.getOrPut(gradleModule.name, { mutableSetOf() }).addAll(kotlinGradleModel.compilerArgumentsBySourceSet.keys)
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }
}

