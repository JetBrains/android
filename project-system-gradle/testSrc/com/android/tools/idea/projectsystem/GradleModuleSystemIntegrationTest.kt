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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat

/**
 * Integration tests for [GradleModuleSystem]; contains tests that require a working gradle project.
 */
class GradleModuleSystemIntegrationTest : AndroidGradleTestCase() {
  @Throws(Exception::class)
  fun testGetAvailableDependencyWithRequiredVersionMatching() {
    loadSimpleApplication()
    verifyProjectDependsOnWildcardAppCompat()
    val moduleSystem = myModules.appModule.getModuleSystem()

    assertThat(isSameArtifact(
      moduleSystem.getLatestCompatibleDependency("com.android.support", "appcompat-v7"),
      GradleCoordinate("com.android.support", "appcompat-v7", "+")
    )).isTrue()
  }

  @Throws(Exception::class)
  fun testGetAvailableDependencyWhenUnavailable() {
    loadSimpleApplication()
    val moduleSystem = myModules.appModule.getModuleSystem()

    assertThat(moduleSystem.getLatestCompatibleDependency("nonexistent", "dependency123")).isNull()
  }

  @Throws(Exception::class)
  fun testRegisterDependency() {
    loadSimpleApplication()
    val moduleSystem = myModules.appModule.getModuleSystem()
    val dependencyManager = GradleDependencyManager.getInstance(project)
    val dummyDependency = GradleCoordinate("a", "b", "+")
    val anotherDummyDependency = GradleCoordinate("hello", "world", "1.2.3")

    moduleSystem.registerDependency(dummyDependency)
    moduleSystem.registerDependency(anotherDummyDependency)

    assertThat(dependencyManager.findMissingDependencies(myModules.appModule, listOf(dummyDependency, anotherDummyDependency))).isEmpty()
  }

  @Throws(Exception::class)
  fun testGetRegisteredExistingDependency() {
    loadSimpleApplication()
    verifyProjectDependsOnWildcardAppCompat()
    val moduleSystem = myModules.appModule.getModuleSystem()

    // Verify that getRegisteredDependency gets a existing dependency correctly.
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    assertThat(moduleSystem.getRegisteredDependency(appCompat)).isNotNull()
    assertThat(moduleSystem.getRegisteredDependency(appCompat)?.revision).isEqualTo("+")
  }

  @Throws(Exception::class)
  fun testGetRegisteredMatchingDependencies() {
    loadSimpleApplication()
    val moduleSystem = myModules.appModule.getModuleSystem()
    val dependencyManager = GradleDependencyManager.getInstance(project)
    val dummyDependency = GradleCoordinate("a", "b", "4.5.6")

    // Setup: Ensure the above dummy dependency is present in the build.gradle file.
    assertThat(dependencyManager.addDependenciesWithoutSync(myModules.appModule, listOf(dummyDependency))).isTrue()
    assertThat(dependencyManager.findMissingDependencies(myModules.appModule, listOf(dummyDependency))).isEmpty()

    assertThat(isSameArtifact(moduleSystem.getRegisteredDependency(GradleCoordinate("a", "b", "4.5.6")), dummyDependency)).isTrue()
    assertThat(isSameArtifact(moduleSystem.getRegisteredDependency(GradleCoordinate("a", "b", "4.5.+")), dummyDependency)).isTrue()
    assertThat(isSameArtifact(moduleSystem.getRegisteredDependency(GradleCoordinate("a", "b", "+")), dummyDependency)).isTrue()
  }

  @Throws(Exception::class)
  fun testGetRegisteredNonMatchingDependencies() {
    loadSimpleApplication()
    val moduleSystem = myModules.appModule.getModuleSystem()
    val dependencyManager = GradleDependencyManager.getInstance(project)
    val dummyDependency = GradleCoordinate("a", "b", "4.5.6")

    // Setup: Ensure the above dummy dependency is present in the build.gradle file.
    assertThat(dependencyManager.addDependenciesWithoutSync(myModules.appModule, listOf(dummyDependency))).isTrue()
    assertThat(dependencyManager.findMissingDependencies(myModules.appModule, listOf(dummyDependency))).isEmpty()

    assertThat(moduleSystem.getRegisteredDependency(GradleCoordinate("a", "b", "4.5.7"))).isNull()
    assertThat(moduleSystem.getRegisteredDependency(GradleCoordinate("a", "b", "4.99.+"))).isNull()
    assertThat(moduleSystem.getRegisteredDependency(GradleCoordinate("a", "BAD", "4.5.6"))).isNull()
  }

  @Throws(Exception::class)
  fun testGetResolvedMatchingDependencies() {
    loadSimpleApplication()
    verifyProjectDependsOnWildcardAppCompat()
    val moduleSystem = myModules.appModule.getModuleSystem()

    // Verify that app-compat is on version 27.1.1 so the checks below make sense.
    assertThat(moduleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))!!.revision).isEqualTo("27.1.1")

    val appCompatDependency = GradleCoordinate("com.android.support", "appcompat-v7", "27.+")
    val wildcardVersionResolution = moduleSystem.getResolvedDependency(appCompatDependency)
    assertThat(wildcardVersionResolution).isNotNull()
    assertThat(wildcardVersionResolution!!.matches(appCompatDependency)).isTrue()
  }

  @Throws(Exception::class)
  fun testGetResolvedNonMatchingDependencies() {
    loadSimpleApplication()
    verifyProjectDependsOnWildcardAppCompat()
    val moduleSystem = myModules.appModule.getModuleSystem()

    // Verify that app-compat is on version 27.1.1 so the checks below make sense.
    assertThat(moduleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))!!.revision).isEqualTo("27.1.1")

    assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "26.+"))).isNull()
    assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.9.0"))).isNull()
    assertThat(moduleSystem.getResolvedDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.+"))).isNull()
  }

  @Throws(Exception::class)
  fun testGetResolvedAarDependencies() {
    loadSimpleApplication()
    verifyProjectDependsOnWildcardAppCompat()

    // appcompat-v7 is a dependency with an AAR.
    assertThat(myModules.appModule.getModuleSystem().getResolvedDependency(
      GradleCoordinate("com.android.support", "appcompat-v7", "+"))).isNotNull()
  }

  @Throws(Exception::class)
  fun testGetResolvedJarDependencies() {
    loadSimpleApplication()
    verifyProjectDependsOnGuava()

    // guava is a dependency with a JAR.
    assertThat(myModules.appModule.getModuleSystem().getResolvedDependency(
      GradleCoordinate("com.google.guava", "guava", "+"))).isNotNull()
  }

  @Throws(Exception::class)
  fun testAddSupportDependencyWithMatchInSubModule() {
    // In this module app -> library2 -> library1
    loadProject(TestProjectPaths.TRANSITIVE_DEPENDENCIES)

    // First verify that there is no support library in the app module:
    val dependency = ProjectBuildModel.get(project).getModuleBuildModel(myModules.appModule)?.dependencies()?.artifacts()
      ?.firstOrNull { it.group().forceString() == SdkConstants.SUPPORT_LIB_GROUP_ID }
    assertThat(dependency).isNull()

    // Check that the version is picked up from one of the sub modules
    val coord = myModules.appModule.getModuleSystem().getLatestCompatibleDependency(SdkConstants.SUPPORT_LIB_GROUP_ID, "recyclerview-v7")
    assertThat(coord!!.id).isEqualTo(SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT)
    assertThat(coord.version.toString()).isEqualTo("+")

    checkFindInReverseDependency()
  }

  // Note this could be a separate test.
  // It is called from [testDependencyInMultiProject] to save time since these tests are slow.
  @Throws(Exception::class)
  private fun checkFindInReverseDependency() {
    // In this module app -> library2 -> library1

    // First verify that there is no gson library in library1:
    val groupId = "com.google.code.gson"
    val dependency = ProjectBuildModel.get(project).getModuleBuildModel(myModules.getModule("library1"))?.dependencies()?.artifacts()
      ?.firstOrNull { it.group().forceString() == groupId }
    assertThat(dependency).isNull()

    // Check that the version is picked up from the parent module:
    val gradleModuleSystem = myModules.getModule("library1").getModuleSystem() as GradleModuleSystem
    val version = gradleModuleSystem.findVersionOfExistingGroupDependency(groupId)
    assertThat(version.toString()).isEqualTo("2.2.4")
  }

  @Throws(Exception::class)
  fun testDependencyInMultiProject() {
    // There are 3 independent modules in this project: module1, module2, module3
    loadProject(TestProjectPaths.SYNC_MULTIPROJECT)

    // First verify that there is a guava library in module3:
    val groupId = "com.google.guava"
    val dependency = ProjectBuildModel.get(project).getModuleBuildModel(myModules.getModule("module3"))?.dependencies()?.artifacts()
      ?.firstOrNull { it.group().forceString() == groupId }
    assertThat(dependency).isNotNull()

    // Check that the version is not picked up from module3 when testing module2:
    val gradleModuleSystem = myModules.getModule("module2").getModuleSystem() as GradleModuleSystem
    val version = gradleModuleSystem.findVersionOfExistingGroupDependency(groupId)
    assertThat(version).isNull()
  }

  private fun isSameArtifact(first: GradleCoordinate?, second: GradleCoordinate?) =
    GradleCoordinate.COMPARE_PLUS_LOWER.compare(first, second) == 0

  private fun verifyProjectDependsOnWildcardAppCompat() {
    // SimpleApplication should have a dependency on "com.android.support:appcompat-v7:+"
    val appCompatArtifact = ProjectBuildModel
        .get(project)
        .getModuleBuildModel(myModules.appModule)
        ?.dependencies()
        ?.artifacts()
        ?.find { "${it.group()}:${it.name().forceString()}" == GoogleMavenArtifactId.APP_COMPAT_V7.toString() }

    assertThat(appCompatArtifact).isNotNull()
    assertThat(appCompatArtifact!!.version().toString()).isEqualTo("+")
  }

  private fun verifyProjectDependsOnGuava() {
    // SimpleApplication should have a dependency on guava.
    assertThat(
      ProjectBuildModel
        .get(project)
        .getModuleBuildModel(myModules.appModule)
        ?.dependencies()
        ?.artifacts()
        ?.find { "${it.group()}:${it.name().forceString()}" == "com.google.guava:guava" }
    ).isNotNull()
  }
}