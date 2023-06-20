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
package com.android.tools.idea.dagger

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import java.io.File

class DaggerDependencyCheckerTest : UsefulTestCase() {

  private lateinit var myFixture: JavaCodeInsightTestFixture

  private lateinit var daggerLib: Module
  private lateinit var appModule: Module

  /** Set up with to modules where mySecondModule depends on myFirstModule. */
  override fun setUp() {
    super.setUp()

    val projectBuilder = JavaTestFixtureFactory.createFixtureBuilder(name)
    myFixture =
      JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)

    val daggerLibFixture = newModule(projectBuilder, "DaggerDependencyCheckerTest_dagger")
    val appModuleFixture = newModule(projectBuilder, "DaggerDependencyCheckerTest_app")

    myFixture.setUp()

    daggerLib = daggerLibFixture.module
    appModule = appModuleFixture.module
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  private fun newModule(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    contentRoot: String
  ): ModuleFixture {
    val firstProjectBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
    val tempDirPath = myFixture.tempDirPath

    // Create a new content root for each module, and create a directory for it manually
    val contentRootPath = "$tempDirPath/$contentRoot"
    File(contentRootPath).mkdir()

    // Call the builder
    return firstProjectBuilder.addContentRoot(contentRootPath).addSourceRoot("src").fixture
  }

  fun test() {
    val appFile =
      myFixture.addFileToProject(
        "DaggerDependencyCheckerTest_app/src/test/MyClass.java",
        // language=JAVA
        """
      package test;

      public class MyClass {}
      """
          .trimIndent()
      )

    assertThat(appFile.project.service<DaggerDependencyChecker>().isDaggerPresent()).isFalse()

    // Make dagger module actually dagger by adding dagger.Module interface
    myFixture.addFileToProject(
      "DaggerDependencyCheckerTest_dagger/src/dagger/Module.java",
      // language=JAVA
      """
      package dagger;

      public @interface Module {}
      """
        .trimIndent()
    )

    // We are not using a Dagger module in an App module, but isDaggerPresent checks for the
    // project.
    assertThat(appFile.project.service<DaggerDependencyChecker>().isDaggerPresent()).isTrue()
  }
}
