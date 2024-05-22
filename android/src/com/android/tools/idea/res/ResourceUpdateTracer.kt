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
package com.android.tools.idea.res

import com.android.ide.common.util.PathString
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.toPathString
import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils.currentTime
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import kotlin.math.max

/** Used to investigate b/167583128. */
object ResourceUpdateTracer {
  @JvmStatic
  var isTracingActive: Boolean = false
    private set

  private val LOG = thisLogger()

  init {
    if (ResourceUpdateTraceSettings.getInstance().enabled) {
      startTracing()
    }
  }

  fun startTracing() {
    FlightRecorder.initialize(StudioFlags.RESOURCE_REPOSITORY_TRACE_SIZE.get())
    isTracingActive = true
  }

  fun stopTracing() {
    isTracingActive = false
  }

  @JvmStatic
  fun dumpTrace(message: String?) {
    val trace = FlightRecorder.getAndClear()
    if (trace.isEmpty()) {
      if (message == null) LOG.info("No resource updates recorded")
      else LOG.info("$message - no resource updates recorded")
      return
    }

    val log = buildString {
      if (!message.isNullOrEmpty()) appendLine(message)
      appendLine("--- Resource update trace: ---")
      trace.forEach { appendLine(it) }
    }
    LOG.info(log)
  }

  @JvmStatic
  fun log(lazyRecord: () -> String) {
    if (isTracingActive) FlightRecorder.log { "$currentTime ${lazyRecord()}" }
  }

  @JvmStatic
  fun logDirect(lazyRecord: () -> String) {
    if (isTracingActive) {
      val message = lazyRecord()
      FlightRecorder.log { "$currentTime $message" }
      LOG.info(message)
    }
  }

  fun pathForLogging(file: VirtualFile): String = file.toPathString().truncatedPathString(6)

  fun pathForLogging(file: PsiFile?): String? = file?.let { pathForLogging(it.virtualFile) }

  @JvmStatic
  fun pathForLogging(file: VirtualFile, project: Project): String =
    pathForLogging(file.toPathString(), project)

  fun pathForLogging(file: PathString, project: Project): String =
    project.guessProjectDir()?.toPathString()?.relativize(file)?.nativePath
      ?: file.truncatedPathString(4)

  fun pathsForLogging(files: Collection<VirtualFile>, project: Project): String =
    files.joinToString(", ") { pathForLogging(it, project) }
}

private fun PathString.truncatedPathString(subdirCount: Int) =
  subpath(max(nameCount - subdirCount, 0), nameCount).nativePath
