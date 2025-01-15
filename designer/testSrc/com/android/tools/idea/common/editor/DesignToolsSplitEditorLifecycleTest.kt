/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.editor.DesignFilesPreviewEditorProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DesignToolsSplitEditorLifecycleTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Suppress("UnstableApiUsage")
  @Test
  fun testSelectNotifyForAsynchronousLoad() = runBlocking {
    val project = projectRule.project
    // Use the real FileEditorManager
    project.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true)
    val delayedRunnable = CompletableDeferred<Runnable>()
    project.replaceService(
      FileEditorManager::class.java,
      object : FileEditorManagerImpl(project, project.coroutineScope) {
        override fun runWhenLoaded(editor: Editor, runnable: Runnable) {
          delayedRunnable.complete(runnable)
        }
      },
      projectRule.fixture.testRootDisposable,
    )
    projectRule.fixture.addFileToProject("AndroidManifest.xml", "")
    val file =
      projectRule.fixture.addFileToProject(
        "res/font/font.xml", /*language=XML */
        """
        <?xml version="1.0" encoding="utf-8"?>
          <font-family xmlns:android="http://schemas.android.com/apk/res/android">
            <font
                android:fontStyle="normal"
                android:fontWeight="400"
                android:font="@font/lobster_regular" />
            <font
                android:fontStyle="italic"
                android:fontWeight="400"
                android:font="@font/lobster_italic" />
        </font-family>
       """
          .trimIndent(),
      )
    file.putUserData(FileEditorProvider.KEY, DesignFilesPreviewEditorProvider())
    val editor =
      runBlocking(uiThread) {
        val editor =
          withContext(uiThread) {
            val editors =
              FileEditorManager.getInstance(projectRule.project)
                .openFile(file.virtualFile, true, true)
            (editors[0] as DesignToolsSplitEditor)
          }
        editor
      }
    assertFalse(
      "The surface must not be active before the editor has completed loading",
      editor.designerEditor.component.surface.isActive,
    )
    delayedRunnable.await().run()
    assertTrue(
      "The surface must be active after the editor has completed loading",
      editor.designerEditor.component.surface.isActive,
    )
  }
}
