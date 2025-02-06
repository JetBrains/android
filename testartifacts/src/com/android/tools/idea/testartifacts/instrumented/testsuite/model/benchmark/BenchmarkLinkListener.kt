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

import com.android.tools.idea.perfetto.PerfettoTraceWebLoader
import com.android.tools.idea.project.AndroidNotification
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.net.URI

private val BENCHMARK_TRACE_FILE_PREFIX_V2 = BenchmarkOutput.BENCHMARK_TRACE_FILE_PREFIX
private val BENCHMARK_TRACE_FILE_PREFIX_V3 = "uri://"

class BenchmarkLinkListener(
  private val project: Project,
  private val isPerfettoWebLoaderEnabled: Boolean = Registry.`is`(PerfettoTraceWebLoader.FEATURE_REGISTRY_KEY, false),
  private val openTraceInPerfettoWebLoader: (file: File, queryParams: String?) -> Unit = PerfettoTraceWebLoader::loadTrace
) : HyperlinkListener {
  override fun hyperlinkClicked(link: String) {
    when {
      link.startsWith(BENCHMARK_TRACE_FILE_PREFIX_V2) || link.startsWith(BENCHMARK_TRACE_FILE_PREFIX_V3) -> {
        val link = convertLinkToV3Format(link) // replace the V2 prefix with a V3 prefix (V3 is backwards compatible)
        check(link.startsWith(BENCHMARK_TRACE_FILE_PREFIX_V3)) // from this point we only deal with the V3 format

        // check if the file exists
        val fileName = link.drop(BENCHMARK_TRACE_FILE_PREFIX_V3.length).replace(Regex("\\?.*"), "") // drop query params (and the prefix)
        val localFile = File(FileUtil.getTempDirectory() + File.separator + fileName)
        if (!localFile.exists()) {
          AndroidNotification.getInstance(project).showBalloon("Benchmark file not found", "Unable to open trace file (${localFile.name})",
                                                               NotificationType.WARNING)
          // TODO (gijosh): Check if we have a task that is currently pulling the file
          return
        }
        val virtualFileTrace = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile) ?: return
        val fd = OpenFileDescriptor(project, virtualFileTrace)

        if (isPerfettoWebLoaderEnabled && isUiPerfettoDevSupportedFile(fileName)) {
          // open the trace in Perfetto Web UI (if applicable)
          val queryParams = Regex("\\?(.*)$").find(link)?.groupValues?.getOrNull(1)
          openTraceInPerfettoWebLoader(fd.file.toIoFile(), queryParams)
        } else {
          // open the trace in the Studio Profiler
          // TODO(b/364596134): pass selection parameters to PerfettoParser to zoom-in on a relevant part of the trace
          FileEditorManager.getInstance(project).openEditor(fd, true)
        }
      }
      link.startsWith("http://") || link.startsWith("https://") -> {
        BrowserLauncher.instance.browse(URI.create(link))
      }

      else -> {
        /* ignore unrecognized links */
      }
    }
  }

  // TODO(b/b/376667704): confirm if perf traces are also supported (and add to supported extensions)
  private fun isUiPerfettoDevSupportedFile(fileName: String) = fileName.endsWith(".perfetto-trace")

  private fun convertLinkToV3Format(link: String): String = when {
    link.startsWith(BENCHMARK_TRACE_FILE_PREFIX_V2) -> BENCHMARK_TRACE_FILE_PREFIX_V3 + link.drop(BENCHMARK_TRACE_FILE_PREFIX_V2.length)
    link.startsWith(BENCHMARK_TRACE_FILE_PREFIX_V3) -> link // no-op
    else -> error("Unsupported Benchmark link format: $link")
  }

  private fun VirtualFile.toIoFile(): File = VfsUtil.virtualToIoFile(this)
}
