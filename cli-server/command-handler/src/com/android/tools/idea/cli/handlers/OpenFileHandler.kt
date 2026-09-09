/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.cli.handlers

import com.android.cliserver.CliActionHandler
import com.android.tools.idea.cli.studio.CommandType
import com.android.tools.idea.cli.studio.OpenFileRequest
import com.android.tools.idea.cli.studio.OpenFileResponse
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

class OpenFileHandler : CliActionHandler {
  override val type: Int = CommandType.OPEN_FILE.number

  override fun handle(projectLookup: () -> Project, request: ByteArray): ByteArray {
    // Look up project here to ensure correct exception handling if no suitable project
    val project = projectLookup()

    val parsedRequest =
      try {
        OpenFileRequest.parseFrom(request)
      } catch (e: Exception) {
        throw Exception("Failed to parse request", e)
      }

    val file = parsedRequest.filePath
    try {
      ApplicationManager.getApplication().invokeAndWait {
        val editors =
          FileEditorManager.getInstance(project)
            .openFile(LocalFileSystem.getInstance().findFileByPath(file) ?: throw Exception("File not found"))
        val editor = editors.getOrNull(0) as? TextEditorWithPreview ?: return@invokeAndWait
        editor.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
      }
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (e: Exception) {
      throw Exception(e.message ?: "Unknown error during file open", e)
    }
    return OpenFileResponse.newBuilder().build().toByteArray()
  }
}
