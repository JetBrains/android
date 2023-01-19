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

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal class TestProjectFixtureRuleImpl(
  private val testProject: TestProjectDefinition
) : FixtureRule<JavaCodeInsightTestFixture> {
  private val tempDirFixture = AndroidProjectRuleTempDirectoryFixture("p")
  private val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
    .createFixtureBuilder("_", tempDirFixture.projectDir.parentFile.toPath(), true)
  private var projectContext_: PreparedTestProject.Context? = null

  override val fixture: JavaCodeInsightTestFixture get() = projectContext_?.fixture ?: noTestYet()

  override var initAndroid: Boolean
    get() = true
    set(_) {
      error("Not supported")
    }

  override var fixtureName: String? = null

  override val testRootDisposable: Disposable = projectBuilder.fixture.testRootDisposable

  val projectRoot: File get() = projectContext_?.projectRoot ?: noTestYet()

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        aggregateAndThrowIfAny {
          usingIdeaTestFixture(tempDirFixture) {
            val tempDirPath = File(tempDirFixture.tempDirPath)
            val fixtureName = fixtureName ?: "p"
            usingIdeaTestFixture(projectBuilder.fixture) {
              withSdksHandled(testRootDisposable) {
                val preparedProject = testProject.prepareTestProject(
                  integrationTestEnvironment = object : IntegrationTestEnvironment {
                    override fun getBaseTestPath(): String = tempDirPath.path
                  },
                  fixtureName,
                  AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
                  null
                )
                preparedProject.open {
                  projectContext_ = this
                  base.evaluate()
                }
              }
            }
          }
        }
      }
    }
  }

  fun selectModule(module: Module) {
    (projectContext_ ?: noTestYet()).selectModule(module)
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

private fun noTestYet(): Nothing {
  error("Test is not yet running")
}

