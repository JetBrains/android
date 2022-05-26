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
package com.android.tools.idea.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.stats.DistributionService
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBLabel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

private const val TEST_JSON = """
[
  {
    "name": "R",
    "version": "11.0",
    "apiLevel": 30,
    "distributionPercentage": 0.283,
    "url": "https://developer.android.com/about/versions/11",
    "descriptionBlocks" : [
      {
        "title": "New features",
        "body": "Chat Bubbles<br>Conversation improvements<br>Wireless debugging<br>Neural Networks API 1.3<br>Frame rate API"
      },
      {
        "title": "Behavior changes",
        "body": "Exposure Notifications<br>Conscrypt SSL engine by default<br>Non-SDK interface restrictions<br>URI access permissions requirements"
      },
      {
        "title": "Security and privacy",
        "body": "Scoped storage enforcement<br>One-time permissions<br>Permissions auto-reset<br>Background location access<br>Package visibility<br>Foreground services<br>Secure sharing of large datasets"
      },
      {
        "title": "",
        "body": "Last updated: May 9th, 2022"
      }
    ]
  }
]
  """

@RunsInEdt
class ChooseApiLevelDialogTest {
  private val projectRule = ProjectRule()

  lateinit var testDirectoryPath: Path
  private val dialog by lazy { ChooseApiLevelDialog(projectRule.project, 0) }

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.project)
    testDirectoryPath = FileUtil.createTempDirectory("ChooseApiLevelDialogTest", null).toPath()
    val file = testDirectoryPath.resolve("test.json").toFile()
    file.writeText(TEST_JSON)
    DistributionService.getInstance().loadFromFile(file.toURI().toURL())
  }

  @After
  fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
  }

  @Test
  fun testLastUpdatedDate() {
    createModalDialogAndInteractWithIt(dialog::show) {
      val label = TreeWalker(dialog.rootPane).descendants().filterIsInstance<JBLabel>()
        .firstOrNull { (it as? JBLabel)?.text == "Last updated: May 9th, 2022" }

      assertThat(label).isNotNull()
    }
  }
}