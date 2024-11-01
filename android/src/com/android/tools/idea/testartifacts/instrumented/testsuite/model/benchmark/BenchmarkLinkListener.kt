/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark

import com.android.tools.idea.project.AndroidNotification
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.net.URI

class BenchmarkLinkListener(private val project: Project) : HyperlinkListener {
  override fun hyperlinkClicked(link: String) {
    when {
      link.startsWith(BenchmarkOutput.BENCHMARK_TRACE_FILE_PREFIX) -> {
        val localFile = File(FileUtil.getTempDirectory() + link.replace(BenchmarkOutput.BENCHMARK_TRACE_FILE_PREFIX, File.separator))
        if (!localFile.exists()) {
          AndroidNotification.getInstance(project).showBalloon("Benchmark file not found", "Unable to open trace file (${localFile.name})",
                                                               NotificationType.WARNING)
          // TODO (gijosh): Check if we have a task that is currently pulling the file
          return
        }
        val virtualFileTrace = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile) ?: return
        val fd = OpenFileDescriptor(project, virtualFileTrace)
        FileEditorManager.getInstance(project).openEditor(fd, true)
      }
      link.startsWith("http://") || link.startsWith("https://") -> {
        BrowserLauncher.instance.browse(URI.create(link))
      }
      else -> { /* ignore unrecognized links */ }
    }
  }
}