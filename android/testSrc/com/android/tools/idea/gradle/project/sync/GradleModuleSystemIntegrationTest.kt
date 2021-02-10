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
package com.android.tools.idea.gradle.project.sync

import com.android.manifmerger.ManifestSystemProperty
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class GradleModuleSystemIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun manifestOverrides() {
    prepareGradleProject(TestProjectToSnapshotPaths.MULTI_FLAVOR, "project")
    openPreparedProject("project") { project ->

      run {
        val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
        expect.that(overrides[ManifestSystemProperty.FUNCTIONAL_TEST]).isNull()
        expect.that(overrides[ManifestSystemProperty.HANDLE_PROFILING]).isNull()
        expect.that(overrides[ManifestSystemProperty.LABEL]).isNull()
        expect.that(overrides[ManifestSystemProperty.MAX_SDK_VERSION]).isNull()
        expect.that(overrides[ManifestSystemProperty.MIN_SDK_VERSION]).isEqualTo("16")
        expect.that(overrides[ManifestSystemProperty.NAME]).isNull()
        expect.that(overrides[ManifestSystemProperty.PACKAGE]).isEqualTo("uninitialized.application.id")
        expect.that(overrides[ManifestSystemProperty.TARGET_PACKAGE]).isNull()
        expect.that(overrides[ManifestSystemProperty.TARGET_SDK_VERSION]).isEqualTo("30")
        expect.that(overrides[ManifestSystemProperty.VERSION_CODE]).isEqualTo("20")
        expect.that(overrides[ManifestSystemProperty.VERSION_NAME]).isEqualTo("1.secondAbc-firstAbc-secondAbc-debug")
        expect.that(ManifestSystemProperty.values().size).isEqualTo(11)
      }
      run {
        switchVariant(project, ":app", "firstXyzSecondXyzRelease")
        val overrides = project.gradleModule(":app")!!.getModuleSystem().getManifestOverrides().directOverrides
        expect.that(overrides[ManifestSystemProperty.FUNCTIONAL_TEST]).isNull()
        expect.that(overrides[ManifestSystemProperty.HANDLE_PROFILING]).isNull()
        expect.that(overrides[ManifestSystemProperty.LABEL]).isNull()
        expect.that(overrides[ManifestSystemProperty.MAX_SDK_VERSION]).isNull()
        expect.that(overrides[ManifestSystemProperty.MIN_SDK_VERSION]).isEqualTo("16")
        expect.that(overrides[ManifestSystemProperty.NAME]).isNull()
        expect.that(overrides[ManifestSystemProperty.PACKAGE]).isEqualTo("uninitialized.application.id")
        expect.that(overrides[ManifestSystemProperty.TARGET_PACKAGE]).isNull()
        expect.that(overrides[ManifestSystemProperty.TARGET_SDK_VERSION]).isEqualTo("30")
        expect.that(overrides[ManifestSystemProperty.VERSION_CODE]).isEqualTo("31")
        expect.that(overrides[ManifestSystemProperty.VERSION_NAME]).isEqualTo("1.0-secondXyz-release")
        expect.that(ManifestSystemProperty.values().size).isEqualTo(11)
      }
    }
  }

  @Test
  fun packageName() {
    prepareGradleProject(TestProjectToSnapshotPaths.MULTI_FLAVOR, "project")
    openPreparedProject("project") { project ->

      run {
        val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
        expect.that(packageName).isEqualTo("com.example.multiflavor")
      }
      run {
        switchVariant(project, ":app", "firstXyzSecondXyzRelease")
        val packageName = project.gradleModule(":app")!!.getModuleSystem().getPackageName()
        expect.that(packageName).isEqualTo("com.example.multiflavor")
      }
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))
}
