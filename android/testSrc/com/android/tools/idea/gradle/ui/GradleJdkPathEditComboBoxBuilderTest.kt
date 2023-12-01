/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.ui

import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.LightPlatformTestCase
import java.io.File
import kotlin.io.path.Path

class GradleJdkPathEditComboBoxBuilderTest: LightPlatformTestCase() {

  fun `test Given empty suggested JDKs, When build ComboBox Then dropdown items contains the embedded JDK`() {
    val jdkComboBox = buildJdkPathEditComboBox(JDK_EMBEDDED_PATH, emptyList())
    assertJdkItems(jdkComboBox, listOf(JDK_EMBEDDED_PATH))
  }

  fun `test Given different suggested JDKs containing embedded one, When build ComboBox Then dropdown items filtered and sorted by version`() {
    val jdkComboBox = buildJdkPathEditComboBox(JDK_EMBEDDED_PATH, listOf(
        JDK_INVALID_PATH, JDK_11_PATH, JDK_EMBEDDED_PATH
    ))
    assertJdkItems(jdkComboBox, listOf(JDK_EMBEDDED_PATH, JDK_11_PATH))
  }

  fun `test Given suggested JDK been canonical equivalent to embedded JDK, When build ComboBox Then dropdown items contains a single embedded JDK`() {
    val jdkComboBox = buildJdkPathEditComboBox(JDK_EMBEDDED_PATH, listOf(
      JDK_EMBEDDED_PATH.replace(File.separator, File.separator + File.separator)
    ))
    assertJdkItems(jdkComboBox, listOf(JDK_EMBEDDED_PATH))
  }

  private fun buildJdkPathEditComboBox(embeddedJdk: String, suggestedJdks: List<String>) =
    GradleJdkPathEditComboBoxBuilder.build(
      currentJdkPath = null,
      embeddedJdkPath = Path(embeddedJdk),
      suggestedJdks = suggestedJdks.map { createMockSdk(it) },
      hintMessage = ""
    )

  private fun assertJdkItems(jdkComboBox: GradleJdkPathEditComboBox, expectedJdkPaths: List<String>) {
    assertEquals(expectedJdkPaths.size, jdkComboBox.itemCount)
    expectedJdkPaths.forEachIndexed { index, expectedJdkPath ->
      val jdkItem = jdkComboBox.getItemAt(index)
      val expectedJdkVersion = JavaSdk.getInstance().getVersionString(expectedJdkPath)
      assertEquals(expectedJdkVersion, jdkItem.label)
      assertEquals(expectedJdkPath, jdkItem.file.toString())
    }
  }

  private fun createMockSdk(mockHomePath: String): Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk("mockJdk", JavaSdk.getInstance())
    sdk.sdkModificator.apply {
      homePath = mockHomePath
      versionString = "mockVersion"
      runWriteAction(::commitChanges)
    }
    return sdk
  }
}