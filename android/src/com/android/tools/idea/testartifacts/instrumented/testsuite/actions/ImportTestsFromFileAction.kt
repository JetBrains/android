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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.android.tools.idea.testartifacts.instrumented.testsuite.export.importAndroidTestMatrixResultXmlFile
import com.intellij.execution.testframework.sm.SmRunnerBundle
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Customized import tests from action which supports additional test format
 * such as UTP test results.
 */
class ImportTestsFromFileAction: AnAction(SmRunnerBundle.message("sm.test.runner.import.test"),
                                          SmRunnerBundle.message("sm.test.runner.import.test.description"),
                                          AllIcons.ToolbarDecorator.Import) {
  override fun actionPerformed(e: AnActionEvent) {
    FileChooser.chooseFile(
      FileChooserDescriptor(true, false, false, false, false, false)
        .withFileFilter {
          it.name == "test-result.pb"
          || FileTypeRegistry.getInstance().isFileOfType(it, XmlFileType.INSTANCE)
        },
      e.project, null) { file ->
      // The file filter does not work on Mac very well. Let's do our best and also
      // handle the situation when users might select the text proto version.
      if (file.extension == "pb" || file.extension == "textproto") {
        ImportUtpResultAction(importFile = file).actionPerformed(e)
      } else {
        object: AbstractImportTestsAction(null, null, null) {
          override fun getFile(project: Project): VirtualFile? = file
          override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            val virtualFile = getFile(project) ?: return
            if (!importAndroidTestMatrixResultXmlFile(project, virtualFile)) {
              // Fallback to the standard IntelliJ test import action.
              super.actionPerformed(e)
            }
          }
        }.actionPerformed(e)
      }
    }
  }
}

