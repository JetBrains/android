/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Ignore
import org.junit.runner.Description

/**
 * A rule that can target an Android+Gradle project.
 *
 * To use it, simply set the path to the target project using the provided [fixture] (see
 * [CodeInsightTestFixture.setTestDataPath]) and then [load] the project.
 */
class AndroidGradleProjectRule : NamedExternalResource() {
  /**
   * This rule is a thin wrapper around [AndroidGradleTestCase], which we delegate to to handle any
   * heavy lifting.
   */
  @Ignore // TestCase used here for its internal logic, not to run tests. Tests will be run by the class that uses this rule.
  private class DelegateGradleTestCase : AndroidGradleTestCase() {
    val fixture: CodeInsightTestFixture get() = myFixture
    val androidFacet: AndroidFacet get() = myAndroidFacet

    fun invokeTasks(project: Project, vararg tasks: String): GradleInvocationResult {
      return AndroidGradleTestCase.invokeGradleTasks(project, *tasks)
    }
  }

  private val delegateTestCase = DelegateGradleTestCase()

  val fixture: CodeInsightTestFixture get() = delegateTestCase.fixture
  val project: Project get() = fixture.project
  val androidFacet: AndroidFacet get() = delegateTestCase.androidFacet

  override fun before(description: Description) {
    delegateTestCase.name = description.methodName
    delegateTestCase.setUp()
  }

  override fun after(description: Description) {
    delegateTestCase.tearDown()
  }

  /**
   * Trigger loading the target Android+Gradle project. Be sure to call [fixture]'s
   * [CodeInsightTestFixture.setTestDataPath] method before calling this.
   */
  fun load(projectPath: String) {
    delegateTestCase.loadProject(projectPath)
  }

  /**
   * Given a project with Gradle support, invoke one or more tasks, e.g. "assembleDebug"
   */
  fun invokeTasks(project: Project, vararg tasks: String): GradleInvocationResult {
    return delegateTestCase.invokeTasks(project, *tasks)
  }
}
