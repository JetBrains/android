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
package com.android.tools.idea.projectsystem

import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID
import com.android.SdkConstants.SUPPORT_LIB_GROUP_ID
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.gradle.CHECK_DIRECT_GRADLE_DEPENDENCIES
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for [GradleModuleSystem]; contains tests that require a working gradle project.
 */
@RunsInEdt
class GradleModuleSystemIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testRegisterDependency() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem()
      val dependencyManager = GradleDependencyManager.getInstance(project)
      val dummyDependency = GradleCoordinate("a", "b", "+")
      val anotherDummyDependency = GradleCoordinate("hello", "world", "1.2.3")

      moduleSystem.registerDependency(dummyDependency)
      moduleSystem.registerDependency(anotherDummyDependency)

      assertThat(
        dependencyManager.findMissingDependencies(project.findAppModule(), listOf(dummyDependency, anotherDummyDependency))
      ).isEmpty()
    }
  }

  @Test
  fun testGetRegisteredExistingDependency() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)
      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that getRegisteredDependency gets a existing dependency correctly.
      val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
      val foundDependency = moduleSystem.getRegisteredDependency(appCompat)!!

      assertThat(foundDependency.artifactId).isEqualTo(APPCOMPAT_LIB_ARTIFACT_ID)
      assertThat(foundDependency.groupId).isEqualTo(SUPPORT_LIB_GROUP_ID)
      assertThat(foundDependency.version!!.major).isEqualTo(28)

      // TODO: b/129297171
      @Suppress("ConstantConditionIf")
      if (CHECK_DIRECT_GRADLE_DEPENDENCIES) {
        // When we were checking the parsed gradle file we were able to detect a specified "+" in the version.
        assertThat(foundDependency.version!!.minorSegment!!.text).isEqualTo("+")
      } else {
        // Now that we are using the resolved gradle version we are no longer able to detect a "+" in the version.
        assertThat(foundDependency.version!!.minor).isLessThan(Integer.MAX_VALUE)
        assertThat(foundDependency.version!!.micro).isLessThan(Integer.MAX_VALUE)
      }
    }
  }

  @Test
  fun testGetRegisteredDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APP_WITH_OLDER_SUPPORT_LIB)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem()
      val appCompat = GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.4.0")

      // Matching Dependencies:
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.4.0")
          ), appCompat
        )
      ).isTrue()
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.4.+")
          ), appCompat
        )
      ).isTrue()
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.+")
          ), appCompat
        )
      ).isTrue()
      assertThat(
        isSameArtifact(
          moduleSystem.getRegisteredDependency(
            GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "+")
          ), appCompat
        )
      ).isTrue()

      // Non Matching Dependencies:
      assertThat(
        moduleSystem.getRegisteredDependency(
          GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "25.0.99")
        )
      ).isNull()
      assertThat(
        moduleSystem.getRegisteredDependency(
          GradleCoordinate(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID, "4.99.+")
        )
      ).isNull()
      assertThat(
        moduleSystem.getRegisteredDependency(
          GradleCoordinate(SUPPORT_LIB_GROUP_ID, "BAD", "25.4.0")
        )
      ).isNull()
    }
  }

  @Test
  fun testGetResolvedMatchingDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)
      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that app-compat is on version 28.0.0 so the checks below make sense.
      assertThat(moduleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))!!.revision).isEqualTo("28.0.0")

      val appCompatDependency = GradleCoordinate("com.android.support", "appcompat-v7", "+")
      val wildcardVersionResolution = moduleSystem.getResolvedDependency(appCompatDependency)
      assertThat(wildcardVersionResolution).isNotNull()
      assertThat(wildcardVersionResolution!!.matches(appCompatDependency)).isTrue()
    }
  }

  @Test
  @Ignore("b/136028658")
  fun testGetResolvedNonMatchingDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)
      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that app-compat is on version 28.0.0 so the checks below make sense.
      assertThat(moduleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))!!.revision).isEqualTo("28.0.0")

      assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "26.+"))).isNull()
      assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.9.0"))).isNull()
      assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.+"))).isNull()
    }
  }

  @Test
  fun testGetResolvedAarDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnWildcardAppCompat(project)

      // appcompat-v7 is a dependency with an AAR.
      assertThat(
        project.findAppModule().getModuleSystem().getResolvedDependency(
          GradleCoordinate("com.android.support", "appcompat-v7", "+")
        )
      ).isNotNull()
    }
  }

  @Test
  fun testGetResolvedJarDependencies() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      verifyProjectDependsOnGuava(project)

      // guava is a dependency with a JAR.
      assertThat(
        project.findAppModule().getModuleSystem().getResolvedDependency(
          GradleCoordinate("com.google.guava", "guava", "+")
        )
      ).isNotNull()
    }
  }

  @Test
  fun testGetDynamicFeatureModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.INSTANT_APP_WITH_DYNAMIC_FEATURES)
    preparedProject.open { project ->
      val moduleSystem = project.findAppModule().getModuleSystem()
      val dynamicFeatureModuleNames = moduleSystem.getDynamicFeatureModules().map { it.name }
      assertThat(dynamicFeatureModuleNames).containsExactly(
        project.findModule("dynamicfeature").getName(),
        project.findModule("instantdynamicfeature").getName()
      ).inOrder()
    }
  }

  @Test
  fun testGetDependencyPath() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->

      val moduleSystem = project.findAppModule().getModuleSystem()

      // Verify that the module system returns a path.
      assertThat(moduleSystem.getDependencyPath(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))).isNotNull()
    }
  }

  private fun isSameArtifact(first: GradleCoordinate?, second: GradleCoordinate?) =
    GradleCoordinate.COMPARE_PLUS_LOWER.compare(first, second) == 0

  private fun verifyProjectDependsOnWildcardAppCompat(project: Project) {
    // SimpleApplication should have a dependency on "com.android.support:appcompat-v7:+"
    val appCompatArtifact = ProjectBuildModel
      .get(project)
      .getModuleBuildModel(project.findAppModule())
      ?.dependencies()
      ?.artifacts()
      ?.find { "${it.group()}:${it.name().forceString()}" == GoogleMavenArtifactId.APP_COMPAT_V7.toString() }

    assertThat(appCompatArtifact).isNotNull()
    assertThat(appCompatArtifact!!.version().toString()).isEqualTo("+")
  }

  private fun verifyProjectDependsOnGuava(project: Project) {
    // SimpleApplication should have a dependency on guava.
    assertThat(
      ProjectBuildModel
        .get(project)
        .getModuleBuildModel(project.findAppModule())
        ?.dependencies()
        ?.artifacts()
        ?.find { "${it.group()}:${it.name().forceString()}" == "com.google.guava:guava" }
    ).isNotNull()
  }
}
