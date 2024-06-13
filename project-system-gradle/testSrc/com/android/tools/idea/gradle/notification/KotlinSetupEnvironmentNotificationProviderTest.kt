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
package com.android.tools.idea.gradle.notification

import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.kotlin.idea.configuration.KotlinSetupEnvironmentNotificationProvider
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class KotlinSetupEnvironmentNotificationProviderTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION).onEdt()

  private val project: Project
    get() = androidProjectRule.project
  private val fixture: CodeInsightTestFixture
    get() = androidProjectRule.fixture

  @Test
  fun `Given java project When adding Kotlin file Then configure Kotlin notification is displayed`() {
    createKotlinFile()
    assertNotificationPanelText(
      KotlinProjectConfigurationBundle.message("kotlin.not.configured")
    )
  }

  private fun createKotlinFile() {
    val kotlinFile = fixture.addFileToProject("/app/src/main/java/google/simpleapplication/TestKotlin.kt", "")
    fixture.openFileInEditor(kotlinFile.virtualFile)
  }

  private fun assertNotificationPanelText(expectedText: String) {
      val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
      val notificationData = KotlinSetupEnvironmentNotificationProvider().collectNotificationData(project, selectedEditor?.file!!)
      val notificationPanel = notificationData?.apply(selectedEditor) as EditorNotificationPanel

      Assert.assertEquals(expectedText, notificationPanel.text)
  }
}