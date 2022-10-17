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
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildMainSourceProviderStub
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Test

@RunsInEdt
class BuildConfigDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.withAndroidModel(
    AndroidProjectBuilder().withMainSourceProvider { buildMainSourceProviderStub() }
  )

  @Test // TODO(xof): fix redirect
  fun testReadMoreUrl() {
    val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/build-config-default", processor.getReadMoreUrl())
  }

  @Test
  fun testEmptyProject() {
    val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    writeToBuildFile(TestFileName("BuildConfigDefault/NoBuildConfigDeclaration"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("BuildConfigDefault/NoBuildConfigDeclaration"))
  }
}