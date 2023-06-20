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
package com.android.tools.idea.nav.safeargs

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.facet.FacetManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Useful test rule for common setup across most safe args tests, which creates a single module
 * application (with module package "test.safeargs").
 *
 * This rule also enables running tests in the EDT. Apply the [RunsInEdt] annotation either on the
 * test class or individual test method to enable it.
 */
class SafeArgsRule(val mode: SafeArgsMode = SafeArgsMode.JAVA) : ExternalResource() {
  private val projectRule = AndroidProjectRule.onDisk()

  val fixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  val androidFacet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  val module
    get() = androidFacet.module

  val project
    get() = module.project

  fun waitForResourceRepositoryUpdates() {
    projectRule.waitForResourceRepositoryUpdates()
  }

  override fun before() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.safeargs">
        <application />
      </manifest>
    """.trimIndent())

    androidFacet.safeArgsMode = mode

    // Add fake "NavArgs" interface to this project so the args class can resolve its interface
    with(fixture.addFileToProject("src/androidx/navigation/NavArgs.java",
      // language=java
      """
        package androidx.navigation;

        public interface NavArgs {}
      """.trimIndent())) {
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    // Add fake "NavDirections" interface to this project so the args class can resolve its interface
    with(fixture.addFileToProject("src/androidx/navigation/NavDirections.java",
      // language=java
      """
        package androidx.navigation;

        public interface NavDirections {}
      """.trimIndent())) {
      fixture.allowTreeAccessForFile(this.virtualFile)
    }
  }

  override fun after() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.clearOverride()
  }

  override fun apply(base: Statement, description: Description): Statement {
    // We want to run tests on the EDT thread, but we also need to make sure the project rule is not
    // initialized on the EDT.
    return RuleChain.outerRule(projectRule).around(EdtRule()).apply(super.apply(base, description), description)
  }

  /**
   * Test utility for adding a fake navigation dependency to our module. This allows testing for the
   * existence / absence of various features that are gated by a certain feature version.
   *
   * If this is not called, it is expected that only base functionality will be provided.
   *
   * Finally, this method should be called before any light classes are generated.
   */
  fun addFakeNavigationDependency(version: Version) {
    val projectSystem = TestProjectSystem(module.project)
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_COMMON, module, GradleVersion.parse(version.toString()))
    projectSystem.useInTests()
  }
}
