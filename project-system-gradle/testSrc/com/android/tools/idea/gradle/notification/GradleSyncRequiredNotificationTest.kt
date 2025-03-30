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

import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.ui.EditorNotificationPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class GradleSyncRequiredNotificationTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION)

  private val project: Project
    get() = androidProjectRule.project
  private val fixture: JavaCodeInsightTestFixture
    get() = androidProjectRule.fixture

  @Test
  fun testModifyingGradleBuildFileDisplaysNotification() {
    openFileInEditor("app/build.gradle", true)
    assertNotificationPanelText(
      "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
  }

  @Test
  fun testModifyingGradleConfigPropertiesFileDisplaysNotification() {
    openFileInEditor(".gradle/config.properties", true)
    assertNotificationPanelText(
      "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
  }

  @Test
  fun testModifyingGradleJvmConfigurationDisplaysNotification() {
    openFileInEditor("build.gradle")
    GradleSyncStateHolder.getInstance(project).recordGradleJvmConfigurationChanged()
    assertNotificationPanelText("Gradle JDK configuration has changed. A project sync may be necessary for the IDE to apply those changes.")
  }

  private fun openFileInEditor(filePath: String, editFile: Boolean = false) {
    WriteCommandAction.runWriteCommandAction(project) {
      val path = filePath.split(File.separator).toTypedArray()
      val buildGradleFile = VfsUtil.findRelativeFile(project.baseDir, *path)
      fixture.openFileInEditor(buildGradleFile!!)
      if (editFile) {
        fixture.editor.executeAndSave { fixture.editor.insertText("test") }
      }
    }
  }

  private fun assertNotificationPanelText(expectedText: String) {
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
    val notificationData = ProjectSyncStatusNotificationProvider(project).collectNotificationData(project, selectedEditor?.file!!)
    val notificationPanel = notificationData?.apply(selectedEditor) as EditorNotificationPanel

    assertEquals(expectedText, notificationPanel.text)
  }
}