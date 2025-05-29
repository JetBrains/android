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
package com.android.tools.idea.wear.dwf.importer.wfs

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NotificationRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import java.awt.Component
import kotlin.io.path.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.only
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ImportWatchFaceStudioFileActionTest {

  private val studioFlagRule = FlagRule(StudioFlags.WATCH_FACE_STUDIO_FILE_IMPORT, true)
  private val projectRule = AndroidProjectRule.inMemory()
  private val notificationRule = NotificationRule(projectRule)

  @get:Rule val ruleChain = RuleChain(studioFlagRule, projectRule, notificationRule)

  private lateinit var action: ImportWatchFaceStudioFileAction
  private val importer = mock<WatchFaceStudioFileImporter>()

  @Before
  fun setup() {
    whenever(importer.supportedFileTypes).thenReturn(setOf("aab", "apk"))
    projectRule.project.replaceService(
      WatchFaceStudioFileImporter::class.java,
      importer,
      projectRule.fixture.testRootDisposable,
    )
    action = ImportWatchFaceStudioFileAction()
  }

  @Test
  fun `the action is disabled if there is no project`() {
    val event = TestActionEvent.createTestEvent(SimpleDataContext.EMPTY_CONTEXT)
    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun `the action is disabled if the flag is disabled`() {
    StudioFlags.WATCH_FACE_STUDIO_FILE_IMPORT.overrideForTest(
      false,
      projectRule.fixture.testRootDisposable,
    )

    val event = TestActionEvent.createTestEvent()
    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun `the action is enabled and visible`() {
    val event = TestActionEvent.createTestEvent()
    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun `the file's path is imported when the file is selected`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val action =
      ImportWatchFaceStudioFileAction(defaultDispatcher = dispatcher, edtDispatcher = dispatcher)
    val path = Path("selected/file/path")
    val file = mock<VirtualFile>()
    whenever(file.toNioPath()).thenReturn(path)
    fakeFileChooser(fileToSelect = file)
    doReturn(WFSImportResult.Success).whenever(importer).import(path)

    action.actionPerformed(TestActionEvent.createTestEvent())
    advanceUntilIdle()

    verify(importer).import(path)
    val successNotification =
      notificationRule.notifications.find {
        it.type == NotificationType.INFORMATION &&
          it.content == "The Watch Face Studio file was imported successfully"
      }
    assertThat(successNotification).isNotNull()
  }

  @Test
  fun `action notifies of an error`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val action =
      ImportWatchFaceStudioFileAction(defaultDispatcher = dispatcher, edtDispatcher = dispatcher)
    val path = Path("selected/file/path")
    val file = mock<VirtualFile>()
    whenever(file.toNioPath()).thenReturn(path)
    fakeFileChooser(fileToSelect = file)
    doReturn(WFSImportResult.Error()).whenever(importer).import(path)

    action.actionPerformed(TestActionEvent.createTestEvent())
    advanceUntilIdle()

    val errorNotification =
      notificationRule.notifications.find {
        it.type == NotificationType.ERROR &&
          it.content == "An error occurred while importing the Watch Face Studio file"
      }
    assertThat(errorNotification).isNotNull()
  }

  @Test
  fun `nothing is imported when no file is selected`() = runTest {
    fakeFileChooser(fileToSelect = null)

    action.actionPerformed(TestActionEvent.createTestEvent())
    advanceUntilIdle()

    verify(importer, only()).supportedFileTypes
  }

  @Suppress("UnstableApiUsage")
  private fun fakeFileChooser(fileToSelect: VirtualFile?) {
    val fileChooserFactory =
      object : FileChooserFactoryImpl() {
        override fun createFileChooser(
          descriptor: FileChooserDescriptor,
          project: Project?,
          parent: Component?,
        ) = FileChooserDialog { project, toSelect -> arrayOf(fileToSelect) }
      }

    ApplicationManager.getApplication()
      .replaceService(
        FileChooserFactory::class.java,
        fileChooserFactory,
        projectRule.fixture.testRootDisposable,
      )
  }
}
