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
package com.android.tools.idea.ui.screenshot

import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.ui.extractTextFromHtml
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.swing.JEditorPane

/** Tests for [DeviceScreenshotSettingsPageTest]. */
@RunsInEdt
internal class DeviceScreenshotSettingsPageTest {

  private val projectRule = ProjectRule()
  @get:Rule
  val ruleChain = RuleChain(projectRule, EdtRule(), HeadlessDialogRule())

  private val project: Project
    get() = projectRule.project

  @Test
  fun testSettingsPage() {
    val provider = DeviceScreenshotSettingsPage.Provider(project)
    assertThat(provider.canCreateConfigurable()).isTrue()
    val settingsPage = provider.createConfigurable()
    Disposer.register(project) {
      settingsPage.disposeUIResources()
    }
    val component = settingsPage.createComponent()!!
    val previewField = component.getDescendant<JEditorPane>()
    assertThat(extractTextFromHtml(previewField.text).substringAfterLast(File.separatorChar))
        .matches("Screenshot_\\d{8}_\\d{6}\\.png")
  }
}