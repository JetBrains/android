/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.testutils.TestUtils
import com.android.tools.idea.flags.DeclarativeStudioSupport
import com.android.tools.idea.gradle.util.GradleWrapper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.runner.Description
import java.io.File

class DeclarativeAndroidGradleProjectRule(val projectRule: AndroidGradleProjectRule) :
  NamedExternalResource() {
  val project: Project
    get() = projectRule.project

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  override fun before(description: Description) {
    projectRule.before(description)
    DeclarativeStudioSupport.override(true)
  }

  override fun after(description: Description) {
    projectRule.after(description)
    DeclarativeStudioSupport.clearOverride()
  }

  @JvmOverloads
  fun loadProject(
    projectPath: String,
    chosenModuleName: String? = null,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor =
      AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    ndkVersion: String? = null,
    preLoad: ((projectRoot: File) -> Unit)? = null
  ) = projectRule.loadProject(projectPath, chosenModuleName, agpVersion, ndkVersion) { projectRoot ->
    WriteCommandAction.runWriteCommandAction(project) {
      setupGradleSnapshotToWrapper(project)
    }
    preLoad?.invoke(projectRoot)
  }

  private fun setupGradleSnapshotToWrapper(project: Project) {
    val distribution = TestUtils.resolveWorkspacePath("tools/external/gradle")
    val gradle = distribution.resolve("gradle-8.12-20241105002153+0000-bin.zip")
    val wrapper = GradleWrapper.find(project)!!
    wrapper.updateDistributionUrl(gradle.toFile())
  }
}

fun AndroidGradleProjectRule.withDeclarative(): DeclarativeAndroidGradleProjectRule =
  DeclarativeAndroidGradleProjectRule(this)