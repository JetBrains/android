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
package com.android.tools.idea.actions

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.UIUtil
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateSampleDataDirectoryTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @Test
  fun createDirectorySuccess() {
    val createAction = CreateSampleDataDirectory()
    val event =
      TestActionEvent(
        SimpleDataContext.builder()
          .add(LangDataKeys.MODULE_CONTEXT_ARRAY, arrayOf(projectRule.module))
          .build(),
        createAction
      )

    createAction.actionPerformed(event)
    assertTrue(Files.exists(Path.of(projectRule.project.basePath!!, "sampledata")))

    // Verify that the action created an Undo command
    val name =
      UIUtil.invokeAndWaitIfNeeded(
        Computable {
          UIUtil.dispatchAllInvocationEvents()
          UndoManager.getInstance(projectRule.project)
            .getUndoActionNameAndDescription(null)
            .first
            .replace("_", "") // Remove keyboard accelerators
        }
      )
    assertEquals("Undo Sample Data Directory", name)
  }
}
