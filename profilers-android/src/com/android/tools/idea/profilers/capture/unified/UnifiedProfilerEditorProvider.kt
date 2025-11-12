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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.capture.CpuCaptureFileType
import com.android.tools.idea.profilers.capture.PerfettoCaptureFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Custom [FileEditorProvider] which allows opening profiler captures in a new editor tab.
 */
class UnifiedProfilerEditorProvider : FileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean {
    // Fail fast if extension is not supported
    val isProfilerCaptureFile = (file.fileType is CpuCaptureFileType ||
                                 PerfettoCaptureFileType.EXTENSIONS.contains(file.extension))

    if (!isProfilerCaptureFile) return false

    if (!file.isValid || !file.isInLocalFileSystem) {
      return false
    }

    return canViewInUnifiedProfiler(file)
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return UnifiedProfilerFileEditor(project, file)
  }

  override fun getEditorTypeId(): String {
    return ID
  }

  override fun getPolicy(): FileEditorPolicy {
    // This policy ensures our editor is used instead of the default one.
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }

  companion object {
    const val ID = "UnifiedProfilerEditorProvider"
    private val log = Logger.getInstance(UnifiedProfilerEditorProvider::class.java)

    @JvmStatic
    fun canViewInUnifiedProfiler(file: VirtualFile): Boolean {
      // This is the feature/experiment flag
      if (!StudioFlags.PROFILER_SYSTEM_TRACE_IN_EDITOR.get()) {
        return false
      }
      try {
        val formats = listOf(PerfettoTraceFormat)
        return formats.any { it.isSupported(file) }
      } catch (e: Exception) {
        // Fallback to false if file cannot be read (e.g. FileNotFoundException)
        log.warn("Error checking file support: ${file.path}", e)
        return false
      }
    }
  }
}
