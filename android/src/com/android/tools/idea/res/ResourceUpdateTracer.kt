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
import com.android.tools.idea.res.ResourceUpdateTraceSettings.Companion.getInstance
import com.android.tools.idea.util.toPathString
import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils.currentTime
import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.math.max

/** Used to investigate b/167583128. */
object ResourceUpdateTracer {
  var isTracingActive: Boolean = false
    private set

  private val LOG = Logger.getInstance(ResourceUpdateTracer::class.java)

  init {
    if (getInstance().enabled) {
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

  fun dumpTrace(message: String?) {
    val trace = FlightRecorder.getAndClear()
    if (trace.isEmpty()) {
      if (message == null) {
        LOG.info("No resource updates recorded")
      } else {
        LOG.info("$message - no resource updates recorded")
      }
    } else {
      val intro = if (Strings.isNullOrEmpty(message)) "" else message + '\n'
      LOG.info(
        """
  $intro--- Resource update trace: ---
  ${Joiner.on('\n').join(trace)}
  ------------------------------
  """
          .trimIndent()
      )
    }
  }

  fun log(lazyRecord: Supplier<*>) {
    if (isTracingActive) {
      FlightRecorder.log { currentTime + ' ' + lazyRecord.get() }
    }
  }

  fun logDirect(lazyRecord: Supplier<*>) {
    if (isTracingActive) {
      val message = lazyRecord.get().toString()
      FlightRecorder.log { currentTime + ' ' + message }
      LOG.info(message)
    }
  }

  fun pathForLogging(file: VirtualFile?): String? {
    if (file == null) {
      return null
    }
    val path = file.toPathString()
    return path
      .subpath(max((path.nameCount - 6).toDouble(), 0.0).toInt(), path.nameCount)
      .nativePath
  }

  fun pathForLogging(file: PsiFile?): String? {
    return if (file == null) null else pathForLogging(file.virtualFile)
  }

  fun pathForLogging(file: VirtualFile?, project: Project): String? {
    if (file == null) {
      return null
    }
    return pathForLogging(file.toPathString(), project)
  }

  fun pathForLogging(file: PathString, project: Project): String {
    val projectDir =
      project.guessProjectDir()
        ?: return file
          .subpath(max((file.nameCount - 4).toDouble(), 0.0).toInt(), file.nameCount)
          .nativePath
    return projectDir.toPathString().relativize(file).nativePath
  }

  fun pathsForLogging(files: Collection<VirtualFile?>, project: Project): String {
    return files
      .stream()
      .map { file: VirtualFile? -> pathForLogging(file, project) }
      .collect(Collectors.joining(", "))
  }
}
