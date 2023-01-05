/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:Suppress("PrivatePropertyName")

package com.android.tools.idea.testing

import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal class TestProjectFixtureRuleImpl(
  private val testProject: TestProjectDefinition
) : FixtureRule<JavaCodeInsightTestFixture> {
  private val tempDirFixture = AndroidProjectRuleTempDirectoryFixture("p")
  private var project_: Project? = null
  private var projectRoot_: File? = null
  private var module_: Module? = null
  val projectRoot: File get() = projectRoot_ ?: noTestYet()

  private val projectTestFixture = object : IdeaProjectTestFixture {
    private val rootDisposable = Disposer.newDisposable()

    // Invoked by JavaCodeInsightTestFixtureImpl.setUp()
    override fun setUp() = Unit

    // Invoked by JavaCodeInsightTestFixtureImpl.tearDown()
    override fun tearDown() = Unit

    override fun getProject(): Project = project_ ?: noTestYet()

    override fun getModule(): Module? = module_ ?: project.gradleModule(":app")?.getMainModule()


    override fun getTestRootDisposable(): Disposable = rootDisposable
  }

  private fun noTestYet(): Nothing = error("Test is not yet running")

  override val fixture: JavaCodeInsightTestFixture = JavaCodeInsightTestFixtureImpl(projectTestFixture, tempDirFixture)

  override var initAndroid: Boolean
    get() = true
    set(_) {
      error("Not supported")
    }

  override var fixtureName: String? = null

  override val testRootDisposable: Disposable
    get() = fixture.testRootDisposable

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
          .createFixtureBuilder("p", tempDirFixture.projectDir.parentFile.toPath(), true)

        aggregateAndThrowIfAny {
          usingIdeaTestFixture(projectBuilder.fixture) {
            withSdksHandled(testRootDisposable) {
              val preparedProject = testProject.prepareTestProject(
                integrationTestEnvironment = object : IntegrationTestEnvironment {
                  override fun getBaseTestPath(): String = tempDirFixture.tempDirPath
                },
                fixtureName ?: "p",
                AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
                null
              )
              projectRoot_ = preparedProject.root
              preparedProject.open { project ->
                project_ = project

                fixture.setUp()
                runCatchingAndRecord { base.evaluate() }
                runCatchingAndRecord { fixture.tearDown() }
              }
              runCatchingAndRecord { Disposer.dispose(projectTestFixture.testRootDisposable) }
            }
          }
        }
      }
    }
  }

  fun selectModule(module: Module) {
    this.module_ = module
  }
}

private inline fun AggregateAndThrowIfAnyContext.withSdksHandled(testRootDisposable: Disposable, body: () -> Unit) {
  val jdk = IdeSdks.getInstance().jdk ?: error("Failed to set JDK")
  Disposer.register(testRootDisposable) {
    runWriteAction { runCatchingAndRecord { ProjectJdkTable.getInstance().removeJdk(jdk) } }
  }
  runCatchingAndRecord { body() }
  runInEdtAndWait { runCatchingAndRecord { removeAllAndroidSdks() } }
}
