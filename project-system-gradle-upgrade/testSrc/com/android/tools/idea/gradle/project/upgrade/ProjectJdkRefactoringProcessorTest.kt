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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.constants.JDK_11_PATH
import com.android.tools.idea.gradle.project.sync.constants.JDK_17_PATH
import com.android.tools.idea.gradle.project.sync.constants.JDK_1_8_PATH
import com.android.tools.idea.sdk.Jdks
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@RunsInEdt
class ProjectJdkRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {

  var jdk17: Sdk? = null

  @Before
  fun ensureJdk17InProjectTable() {
    // for whatever reason, without this the project is set up using prebuilts/studio/jdk/mock-jdk17, which despite its name is
    // treated as Java 1.5.
    jdk17 = Jdks.getInstance().createJdk(JDK_17_PATH)
  }

  @After
  fun removeJdk17FromProjectTable() {
    jdk17?.let { runWriteAction { ProjectJdkTable.getInstance().removeJdk(it) } }?.also { jdk17 = null }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = ProjectJdkRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/project-jdk-needs-upgrade", processor.getReadMoreUrl())
  }

  @Test
  fun testUpdateFromJdk8() {
    setGradleInstallationPath(JDK_1_8_PATH)
    val processor = ProjectJdkRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
    assertThat(processor.findUsages()).isNotEmpty()
  }

  @Test
  fun testUpdateFromJdk11() {
    setGradleInstallationPath(JDK_11_PATH)
    val processor = ProjectJdkRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
    assertThat(processor.findUsages()).isNotEmpty()
  }

  @Test
  fun testNoUpdateFromJdk17() {
    setGradleInstallationPath(JDK_17_PATH)
    val processor = ProjectJdkRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isBlocked)
    assertThat(processor.findUsages()).isEmpty()
  }

  @Test
  fun testIsBlockedFromJdk8ToJdk11() {
    setGradleInstallationPath(JDK_1_8_PATH)
    val processor = ProjectJdkRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("7.0.0"))
    // This is blocked because the Project JDK Table has no entry for JDK11
    assertTrue(processor.isBlocked)
  }

  private fun setGradleInstallationPath(path: String) {
    val installationManager: GradleInstallationManager = mock()
    whenever(installationManager.getGradleJvmPath(any(), any())).thenReturn(path)
    projectRule.replaceService(GradleInstallationManager::class.java, installationManager)
  }
}