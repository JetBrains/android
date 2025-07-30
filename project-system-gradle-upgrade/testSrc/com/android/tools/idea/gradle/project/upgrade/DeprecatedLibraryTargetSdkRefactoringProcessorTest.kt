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
class DeprecatedLibraryTargetSdkRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {

  @Test
  fun testTargetSdkIsRemovedFromLibraryBuildFile() {
    writeToBuildFile(TestFileName("RemoveLibraryBaseFlavorTargetSdk/LibraryWithTargetSdk"))

    val processor = DeprecatedLibraryTargetSdkRefactoringProcessor(
      project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveLibraryBaseFlavorTargetSdk/LibraryWithTargetSdkExpected"))
  }

  @Test
  fun testTargetSdkIsNotRemovedFromApplicationBuildFile() {
    writeToBuildFile(TestFileName("RemoveLibraryBaseFlavorTargetSdk/ApplicationWithTargetSdk"))

    val processor = DeprecatedLibraryTargetSdkRefactoringProcessor(
      project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveLibraryBaseFlavorTargetSdk/ApplicationWithTargetSdk"))
  }
}