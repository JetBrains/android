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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl.Companion.fromLibraryTables
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.gradle.project.sync.internal.dumpAllVariantsSyncAndroidModuleModel
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

/**
 * Tests for AllVariantsSync Sync option.
 * The tests run Sync using both V1 and V2. We first run Sync using the normal process, and verify that we only request one variant per gradle project.
 * The second step is to run Sync request (fetch gradle models) using GradleSyncExecutor (used in the PSD workflow), and verify that we
 * get all the gradle project's variants created.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest].
 */
@RunsInEdt
class AllVariantsSyncWithGradleSyncExecutorTest : SnapshotComparisonTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val testName: TestName = TestName()

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/IdeModels_AllVariantsSync"

  override fun getName(): String = testName.methodName


  @Test
  fun testAllVariantSyncWithV2() {
    // Load the project and run Sync (SVS in this case).
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      runSvsAndAvsSyncAndVerifyFetchedVariants(project, 12)
    }
  }

  @Test
  fun testAllVariantWithBuildSrc() {
    // Load the project and run Sync (SVS in this case).
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.APP_WITH_BUILDSRC)
    preparedProject.open { project ->
      runSvsAndAvsSyncAndVerifyFetchedVariants(project, 2)
    }
  }

  private fun runSvsAndAvsSyncAndVerifyFetchedVariants(project: Project, variantsExpectedInModule0: Int) {
    val syncExecutor = GradleSyncExecutor(project)
    val appModule = project.findAppModule()
    val svsAndroidModel = GradleAndroidModel.get(appModule)
    // Since we ran a SVS Sync, we should only have one fetched variant.
    Truth.assertThat(svsAndroidModel!!.variants.size).isEqualTo(1)

    // Run AllVariantsSync using the GradleSyncExecutor.
    val gradleModules = runWithModalProgressBlocking(project, "Test Android Gradle Sync") {
      coroutineToIndicator {
        syncExecutor.fetchGradleModels()
      }
    }
    val allVariantsSyncAndroidModel = gradleModules.modules[0].findModel(GradleAndroidModelData::class.java)
    Truth.assertThat(allVariantsSyncAndroidModel).isNotNull()
    // Assert that we fetched all the variants of the module in this case.
    Truth.assertThat(allVariantsSyncAndroidModel!!.variants.size).isEqualTo(variantsExpectedInModule0)
    // Dump the GradleAndroidModel.
    val dumper = ProjectDumper(
      additionalRoots = mapOf("ROOT" to File(project.basePath!!)),
      projectJdk = ProjectRootManager.getInstance(project).projectSdk,
    )
    val modelFactory = GradleAndroidModel.createFactory(
      project, fromLibraryTables(gradleModules.libraries!!, null)
    )
    dumper.dumpAllVariantsSyncAndroidModuleModel(
      modelFactory(allVariantsSyncAndroidModel),
      project.basePath!!
    )
    // Verify dump content matches expected snapshot files.
    val dump = dumper.toString().trimIndent()

    assertIsEqualToSnapshot(dump)
  }
}