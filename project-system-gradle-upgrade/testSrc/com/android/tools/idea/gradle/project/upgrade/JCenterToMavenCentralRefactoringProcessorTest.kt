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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class JCenterToMavenCentralRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testBuildWithJCenterOnly() {
    writeToBuildFile(TestFileName("JCenterToMavenCentral/BuildWithJCenterOnly"))
    val processor = JCenterToMavenCentralRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("JCenterToMavenCentral/BuildWithJCenterOnlyExpected"))
  }

  @Test
  fun testBuildWithJCenterAndMavenCentral() {
    writeToBuildFile(TestFileName("JCenterToMavenCentral/BuildWithJCenterAndMavenCentral"))
    val processor = JCenterToMavenCentralRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("JCenterToMavenCentral/BuildWithJCenterAndMavenCentralExpected"))
  }

  @Test
  fun testSettingsWithJCenterOnly() {
    writeToSettingsFile(TestFileName("JCenterToMavenCentral/SettingsWithJCenterOnly"))
    val processor = JCenterToMavenCentralRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("JCenterToMavenCentral/SettingsWithJCenterOnlyExpected"))
  }

  @Test
  fun testSettingsWithJCenterAndMavenCentral() {
    writeToSettingsFile(TestFileName("JCenterToMavenCentral/SettingsWithJCenterAndMavenCentral"))
    val processor = JCenterToMavenCentralRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(settingsFile, TestFileName("JCenterToMavenCentral/SettingsWithJCenterAndMavenCentralExpected"))
  }

}