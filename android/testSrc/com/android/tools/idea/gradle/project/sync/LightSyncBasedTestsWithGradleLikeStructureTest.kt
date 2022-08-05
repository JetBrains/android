/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.testing.AndroidLibraryDependency
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.buildNdkModelStub
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.android.tools.idea.testing.updateTestProjectFromAndroidModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import java.io.File

/**
 * A test case that ensures the correct behavior of [AndroidProjectRule.withAndroidModels] way to set up test projects.
 *
 * See [AndroidProjectRule.withAndroidModels] for more details.
 */
@RunsInEdt
class LightSyncBasedTestsWithGradleLikeStructureTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  val projectRule = AndroidProjectRule.withAndroidModel(AndroidProjectBuilder()).named(this::class.simpleName)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"

  @Test
  fun testLightTestsWithGradleLikeStructure() {
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }
}

/**
 * A test case that ensures the correct behavior of [AndroidProjectRule.withAndroidModels] way to set up test projects with NDK models.
 *
 * See [AndroidProjectRule.withAndroidModels] for more details.
 */
@RunsInEdt
class LightSyncBasedTestsWithCMakeLikeStructureTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  val projectRule = AndroidProjectRule.withAndroidModel(
    AndroidProjectBuilder(
      ndkModel = { buildNdkModelStub() }
    )
  ).named(this::class.simpleName)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"

  @Test
  fun testLightTestsWithCMakeLikeStructure() {
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }
}

@RunsInEdt
class LightSyncBasedTestsWithDefaultTestProjectStructureTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  val projectRule =
    AndroidProjectRule.withAndroidModel(createAndroidProjectBuilderForDefaultTestProjectStructure()).named(this::class.simpleName)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"

  @Test
  fun testLightTestsWithDefaultTestProjectStructure() {
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }
}

@RunsInEdt
class LightSyncBasedTestsWithMultipleModulesTestProjectStructureTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  val projectRule =
    AndroidProjectRule.withAndroidModels(rootModuleBuilder, appModuleBuilder, libModuleBuilder).named(this::class.simpleName)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"

  @Test
  fun testLightTestsWithMultipleModulesTestProjectStructure() {
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }
}

class LightSyncForAndroidTestCaseTest : AndroidTestCase(), SnapshotComparisonTest {
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"

  @Test
  fun testLightTestsWithDefaultTestProjectStructureForAndroidTestCase() {
    setupTestProjectFromAndroidModel(
      project,
      File(myFixture.tempDirPath),
      AndroidModuleModelBuilder(":", "debug", createAndroidProjectBuilderForDefaultTestProjectStructure())
    )
    val dump = project.saveAndDump(additionalRoots = mapOf("TEMP" to File(myFixture.tempDirPath)))
    assertIsEqualToSnapshot(dump)
  }

  @Test
  fun testLightTestsWithMultipleModulesTestProjectStructureInAndroidTestCase() {
    setupTestProjectFromAndroidModel(
      project, File(myFixture.tempDirPath), rootModuleBuilder, appModuleBuilder, libModuleBuilder)
    val dump = project.saveAndDump(additionalRoots = mapOf("TEMP" to File(myFixture.tempDirPath)))
    assertIsEqualToSnapshot(dump)
  }

  @Test
  fun testLightTestsWithMultipleModulesTestProjectStructureInAndroidTestCase_resyncing() {
    val tempRoot = File(myFixture.tempDirPath)
    setupTestProjectFromAndroidModel(
      project,
      tempRoot,
      rootModuleBuilder,
      appModuleBuilder,
      libModuleBuilder
    )
    val dump = project.saveAndDump(additionalRoots = mapOf("TEMP" to tempRoot))

    // Do not request before setup as it replaces the project system implementation.
    val syncModificationTracker = ProjectSyncModificationTracker.getInstance(project)
    val syncStamp = syncModificationTracker.modificationCount
    updateTestProjectFromAndroidModel(
      project,
      tempRoot,
      rootModuleBuilder,
      appModuleBuilder,
      libModuleBuilderWithLib(tempRoot.resolve(".gradle"))
    )
    assertThat(syncModificationTracker.modificationCount).isGreaterThan(syncStamp)
    val dumpAfter = project.saveAndDump(additionalRoots = mapOf("TEMP" to tempRoot))

    assertAreEqualToSnapshots(dump to ".before", dumpAfter to ".after")
  }
}

private val appModuleBuilder = AndroidModuleModelBuilder(
  ":app",
  "debug",
  AndroidProjectBuilder(androidModuleDependencyList = { listOf(AndroidModuleDependency(":lib", "debug")) })
)

private val libModuleBuilder = AndroidModuleModelBuilder(":lib", "debug", AndroidProjectBuilder())
private fun libModuleBuilderWithLib(gradleCacheRoot: File) =
  AndroidModuleModelBuilder(
    ":lib",
    "debug",
    AndroidProjectBuilder(
      androidLibraryDependencyList = { listOf(ideAndroidLibrary(gradleCacheRoot, "com.example:library:1.0")) }
    )
  )

private fun ideAndroidLibrary(gradleCacheRoot: File, artifactAddress: String) =
  AndroidLibraryDependency(
    IdeAndroidLibraryImpl.create(
      artifactAddress = artifactAddress,
      name = "",
      folder = gradleCacheRoot.resolve(File("libraryFolder")),
      manifest = "manifest.xml",
      compileJarFiles = listOf("api.jar"),
      runtimeJarFiles = listOf("file.jar"),
      resFolder = "res",
      resStaticLibrary = File("libraryFolder/res.apk"),
      assetsFolder = "assets",
      jniFolder = "jni",
      aidlFolder = "aidl",
      renderscriptFolder = "renderscriptFolder",
      proguardRules = "proguardRules",
      lintJar = "lint.jar",
      externalAnnotations = "externalAnnotations",
      publicResources = "publicResources",
      artifact = gradleCacheRoot.resolve(File("artifactFile")),
      symbolFile = "symbolFile",
      deduplicate = { this }
    )
  )
