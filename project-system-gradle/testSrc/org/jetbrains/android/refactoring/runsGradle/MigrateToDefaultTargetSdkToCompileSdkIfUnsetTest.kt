/*
 * Copyright (C) 2025 The Android Open Source Project
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
package org.jetbrains.android.refactoring.runsGradle

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.refactoring.MigrateToDefaultTargetSdkToCompileSdkIfUnsetHandler
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class MigrateToDefaultTargetSdkToCompileSdkIfUnsetTest {

  val projectRule = AndroidGradleProjectRule()

  @JvmField
  @Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Test
  @RunsInEdt
  fun testTargetSdkDefaultsToMinSdkRefactoring() {
    projectRule.loadProject(
      TestProjectPaths.PROJECT_WITH_APP_AND_LIB_WITHOUT_TARGET_SDK,
      agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST)

    val project = projectRule.project

    ApplicationManager.getApplication().invokeAndWait {
      projectRule.fixture.openFileInEditor(project.baseDir.findChild("gradle.properties")!!)
      projectRule.fixture.openFileInEditor(project.baseDir.findChild("app")!!.findChild("build.gradle")!!)
    }

    val appBuildGradle = project.baseDir.findChild("app")!!.findChild("build.gradle")!!
    val gradleProperties = project.baseDir.findChild("gradle.properties")!!

    Truth.assertThat(VfsUtil.loadText(appBuildGradle)).doesNotContain("targetSdkVersion")
    Truth.assertThat(VfsUtil.loadText(gradleProperties)).contains("android.sdk.defaultTargetSdkToCompileSdkIfUnset=false")

    MigrateToDefaultTargetSdkToCompileSdkIfUnsetHandler().invoke(project, null, null, null)

    Truth.assertThat(VfsUtil.loadText(appBuildGradle)).contains("targetSdkVersion '21'")

    // This property should be removed for AGP 9.0.0+ and set to 'true' for AGP versions below 9.0.0.
    Truth.assertThat(VfsUtil.loadText(gradleProperties)).contains("android.sdk.defaultTargetSdkToCompileSdkIfUnset=true")
  }
}