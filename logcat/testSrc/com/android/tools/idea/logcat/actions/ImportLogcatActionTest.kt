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
package com.android.tools.idea.logcat.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.testing.ApplicationServiceRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import java.awt.Component
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.writeText
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

/** Tests for [ImportLogcatAction] */
class ImportLogcatActionTest {
  private val projectRule = ProjectRule()
  private val fakeFileChooserFactory = FakeFileChooserFactory()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ApplicationServiceRule(FileChooserFactory::class.java, fakeFileChooserFactory),
    )

  @Test
  fun actionPerformed() {
    val action = ImportLogcatAction()
    val mockLogcatPresenter = mock<LogcatPresenter>()

    action.actionPerformed(testEvent(mockLogcatPresenter))

    verify(mockLogcatPresenter).openLogcatFile(fakeFileChooserFactory.virtualFile.toNioPath())
  }

  private fun testEvent(logcatPresenter: LogcatPresenter) =
    TestActionEvent.createTestEvent(
      MapDataContext(
        mapOf(
          CommonDataKeys.PROJECT to projectRule.project,
          LogcatPresenter.LOGCAT_PRESENTER_ACTION to logcatPresenter,
        )
      )
    )

  private class FakeFileChooserFactory : FileChooserFactoryImpl() {
    private val fileSystem = createInMemoryFileSystem()
    private val path =
      this@FakeFileChooserFactory.fileSystem.getPath("file.logcat").apply { writeText("") }
    val virtualFile =
      object : LightVirtualFile(path.name) {
        override fun toNioPath(): Path = this@FakeFileChooserFactory.fileSystem.getPath(name)
      }

    override fun createFileChooser(
      descriptor: FileChooserDescriptor,
      project: Project?,
      parent: Component?
    ): FileChooserDialog =
      object : FileChooserDialog {
        override fun choose(project: Project?, vararg toSelect: VirtualFile?): Array<VirtualFile> = arrayOf(virtualFile)
      }
  }
}