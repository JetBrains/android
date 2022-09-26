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
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.project.importing.withAfterCreate
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.testing.AgpIntegrationTestUtil.maybeCreateJdkOverride
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.SystemIndependent
import org.junit.Ignore
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import java.io.File

/**
 * A rule that can target an Android+Gradle project.
 *
 * To use it, simply set the path to the target project using the provided [fixture] (see
 * [CodeInsightTestFixture.setTestDataPath]) and then [load] the project.
 */
class AndroidGradleProjectRule(val workspaceRelativeTestDataPath: @SystemIndependent String = "tools/adt/idea/android/testData") : NamedExternalResource() {
  /**
   * This rule is a thin wrapper around [AndroidGradleTestCase], which we delegate to to handle any
   * heavy lifting.
   */
  @Ignore("TestCase used here for its internal logic, not to run tests. Tests will be run by the class that uses this rule.")
  private inner class DelegateGradleTestCase : AndroidGradleTestCase() {
    val fixture: CodeInsightTestFixture get() = myFixture
    override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = workspaceRelativeTestDataPath

    fun invokeTasks(project: Project, timeoutMillis: Long?, vararg tasks: String): GradleInvocationResult {
      return invokeGradleTasks(project, timeoutMillis, *tasks)
    }

    public override fun generateSources() { // Changes visibility only.
      super.generateSources()
    }
  }

  private val delegateTestCase = DelegateGradleTestCase()

  val fixture: CodeInsightTestFixture get() = delegateTestCase.fixture
  val project: Project get() = fixture.project

  fun androidFacet(gradlePath: String): AndroidFacet = findGradleModule(gradlePath)?.androidFacet ?: gradleModuleNotFound(gradlePath)
  fun gradleModule(gradlePath: String): Module = findGradleModule(gradlePath) ?: gradleModuleNotFound(gradlePath)
  fun findGradleModule(gradlePath: String): Module? = project.gradleModule(gradlePath)

  fun getModule(moduleName: String) = delegateTestCase.getModule(moduleName)
  fun hasModule(moduleName: String) = delegateTestCase.hasModule(moduleName)

  override fun before(description: Description) {
    delegateTestCase.name = description.methodName ?: description.displayName
    delegateTestCase.setUp()
  }

  override fun after(description: Description) {
    delegateTestCase.tearDown()
  }

  /**
   * Triggers loading the target Android Gradle project. Be sure to call [fixture]'s
   * [CodeInsightTestFixture.setTestDataPath] method before calling this method.
   *
   * @param preLoad If specified, gives the caller the opportunity to modify the project before it
   *   is actually loaded.
   */
  @JvmOverloads
  fun load(
    projectPath: String,
    agpVersion: AgpVersionSoftwareEnvironment = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    ndkVersion: String? = null,
    preLoad: ((projectRoot: File) -> Unit)? = null
  ) = loadProject(
    projectPath = projectPath,
    chosenModuleName = null,
    agpVersion = agpVersion,
    ndkVersion = ndkVersion,
    preLoad = preLoad
  )

  /**
   * Triggers loading the target Android Gradle project. Be sure to call [fixture]'s
   * [CodeInsightTestFixture.setTestDataPath] method before calling this method.
   *
   * @param chosenModuleName If specified, which module will be used.
   * @param gradleVersion If specified, which Gradle version will be used.
   * @param agpVersion If specified, which AGP version will be used.
   * @param kotlinVersion If specified, which kotlin version will be used.
   * @param ndkVersion If specified, which NDK version will be used.
   */
  @JvmOverloads
  fun loadProject(
    projectPath: String,
    chosenModuleName: String? = null,
    agpVersion: AgpVersionSoftwareEnvironment = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    ndkVersion: String? = null,
    preLoad: ((projectRoot: File) -> Unit)? = null
  ) {
    val resolvedAgpVersion = agpVersion.resolve()
    val jdkOverride: Sdk? = maybeCreateJdkOverride(resolvedAgpVersion.jdkVersion)
    if (jdkOverride != null) {
      Disposer.register(delegateTestCase.testRootDisposable) {
        runWriteActionAndWait {
          ProjectJdkTable.getInstance().removeJdk(jdkOverride)
        }
      }
    }

    fun afterCreate(project: Project) {
      if (jdkOverride != null) {
        runWriteActionAndWait {
          ProjectRootManager.getInstance(project).projectSdk = jdkOverride
        }
      }
    }

    GradleProjectImporter.withAfterCreate(afterCreate = ::afterCreate) {
      if (preLoad != null) {
        val rootFile = delegateTestCase.prepareProjectForImport(projectPath, resolvedAgpVersion, ndkVersion)

        preLoad(rootFile)
        delegateTestCase.importProject(resolvedAgpVersion.jdkVersion)
        delegateTestCase.prepareProjectForTest(project, chosenModuleName)
      } else {
        delegateTestCase.loadProject(projectPath, chosenModuleName, resolvedAgpVersion, ndkVersion)
      }
    }
  }

  fun requestSyncAndWait() {
    delegateTestCase.requestSyncAndWait()
  }

  fun requestSyncAndWait(request: GradleSyncInvoker.Request) {
    delegateTestCase.requestSyncAndWait(request)
  }

  fun generateSources() {
    delegateTestCase.generateSources()
  }

  /**
   * Invoke one or more tasks, e.g. "assembleDebug"
   */
  fun invokeTasks(vararg tasks: String): GradleInvocationResult {
    return invokeTasks(null, *tasks)
  }

  fun invokeTasks(timeoutMillis: Long?, vararg tasks: String): GradleInvocationResult {
    return delegateTestCase.invokeTasks(project, timeoutMillis, *tasks)
  }

  fun resolveTestDataPath(relativePath: String): File = delegateTestCase.resolveTestDataPath(relativePath)
}

private fun gradleModuleNotFound(gradlePath: String): Nothing =
  throw RuntimeException("No module with Gradle path: $gradlePath")

class EdtAndroidGradleProjectRule(val projectRule: AndroidGradleProjectRule) :
  TestRule by RuleChain.outerRule(projectRule).around(EdtRule())!! {
  val project: Project get() = projectRule.project
  val fixture: CodeInsightTestFixture get() = projectRule.fixture

  @JvmOverloads
  fun loadProject(
    projectPath: String,
    chosenModuleName: String? = null,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    ndkVersion: String? = null
  ) = projectRule.loadProject(projectPath, chosenModuleName, agpVersion, ndkVersion)
}

fun AndroidGradleProjectRule.onEdt(): EdtAndroidGradleProjectRule = EdtAndroidGradleProjectRule(this)
