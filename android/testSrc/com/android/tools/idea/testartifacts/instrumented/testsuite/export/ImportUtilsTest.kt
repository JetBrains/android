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
package com.android.tools.idea.testartifacts.instrumented.testsuite.export

import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.charset.StandardCharsets

@RunWith(JUnit4::class)
@RunsInEdt
class ImportUtilsTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(temporaryDirectoryRule)

  @Test
  fun importShouldSuccess() {
    lateinit var xmlFile: VirtualFile
    runWriteAction {
      val inputDir = temporaryDirectoryRule.newVirtualDirectory("inputDir")
      xmlFile = inputDir.createChildData(this, "testHistory.xml")
      xmlFile.setBinaryContent(
        Resources.toString(
          Resources.getResource("com/android/tools/idea/testartifacts/instrumented/testsuite/export/testHistory.xml"),
          StandardCharsets.UTF_8).toByteArray())
    }

    assertThat(importAndroidTestMatrixResultXmlFile(projectRule.project, xmlFile)).isTrue()
  }
}