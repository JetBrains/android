/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testSingleLiteralProperties() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/SingleLiteralProperties"))
    val processor = MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("MigratePackagingOptions/SingleLiteralPropertiesExpected"))
  }

  @Test
  fun testMultipleLiteralProperties() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/MultipleLiteralProperties"))
    val processor = MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("MigratePackagingOptions/MultipleLiteralPropertiesExpected"))
  }

  @Test
  fun testMultipleReferenceProperties() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/MultipleReferenceProperties"))
    val processor = MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("MigratePackagingOptions/MultipleReferencePropertiesExpected"))
  }

  @Test
  fun testMultipleUnresolvedReferenceProperties() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/MultipleUnresolvedReferenceProperties"))
    val processor = MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("MigratePackagingOptions/MultipleUnresolvedReferencePropertiesExpected"))
  }

  @Test
  fun testGlobsToBoth() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/GlobsToBoth"))
    val processor = MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("9.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("MigratePackagingOptions/GlobsToBothExpected"))
  }
}