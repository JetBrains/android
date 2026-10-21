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
import com.android.tools.idea.profilers.capture.unified.UnifiedProfilerEditorProvider.Companion.isSupportedByPerfettoEditor
import com.android.tools.profilers.ProfilerFormat
// JetBrains patch: the sherlock.common module is not part of the monorepo and Google publishes no artifact for it,
// so com.android.tools.sherlock.common.system.editor.PerfettoEditorStateManager and PerfettoFileEditorState
// cannot be imported here. This is permanent, not a pending merge step.
// import com.android.tools.sherlock.common.system.editor.PerfettoEditorStateManager
// import com.android.tools.sherlock.common.system.editor.PerfettoFileEditorState
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

/**
 * Custom [FileEditorProvider] which allows opening profiler captures in a new editor tab. Also accommodates state persistence (e.g.,
 * zoom/scroll) for supported formats like Perfetto and ART traces.
 */
class UnifiedProfilerEditorProvider : FileEditorProvider, DumbAware {

  // JetBrains patch: both overrides do nothing but delegate to PerfettoEditorStateManager, which lives in the
  // sherlock.common module the monorepo does not carry and for which no artifact is published. With no manager to
  // delegate to, the platform defaults for writeState and readState are what is left, so the overrides stay
  // commented out. This is permanent, not a pending merge step.
  // override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
  //   if (state is PerfettoFileEditorState) {
  //     project.getService(PerfettoEditorStateManager::class.java).writeState(state, targetElement)
  //   }
  // }
  //
  // override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
  //   if (isSupportedByPerfettoEditor(file)) {
  //     return project.getService(PerfettoEditorStateManager::class.java).readState(sourceElement, file)
  //   }
  //   return FileEditorState.INSTANCE
  // }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!TrustedProjects.isProjectTrusted(project)) return false

    // Fail fast if extension is not supported
    val isProfilerCaptureFile =
      (file.fileType is CpuCaptureFileType ||
        PerfettoCaptureFileType.EXTENSIONS.contains(file.extension) ||
        ProfilerFormat.isMemoryFormat(ProfilerFormat.find(file.extension, getLazyTraceType(file))))

    if (!isProfilerCaptureFile) return false

    if (!file.isValid || !file.isInLocalFileSystem) {
      return false
    }

    return canViewInUnifiedEditor(file)
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

    /** Determines whether the given [file] can be parsed and rendered specifically by the Perfetto editor. */
    @JvmStatic
    fun isSupportedByPerfettoEditor(file: VirtualFile): Boolean {
      val formats = mutableListOf<ProfilerFormat>()
      if (StudioFlags.PROFILER_SYSTEM_TRACE_IN_EDITOR.get()) {
        formats.add(ProfilerFormat.PerfettoTrace)
      }
      if (StudioFlags.PROFILER_METHOD_TRACE_IN_EDITOR.get()) {
        formats.add(ProfilerFormat.ArtTrace)
      }

      if (formats.isEmpty()) {
        return false
      }

      val lazyTraceType = getLazyTraceType(file)

      try {
        return formats.any { it.matches(file.extension, lazyTraceType) }
      } catch (e: Exception) {
        // Fallback to false if file cannot be read (e.g. FileNotFoundException)
        log.warn("Error checking file support: ${file.path}", e)
        return false
      }
    }

    /**
     * Serves as a general support check to determine if the given [file] can be opened within the Unified Profiler. While currently this
     * delegates to [isSupportedByPerfettoEditor], it is designed to support future file types that may be opened in the Unified Profiler
     * but rendered by different, non-Perfetto editors.
     */
    @JvmStatic
    fun canViewInUnifiedEditor(file: VirtualFile): Boolean {
      if (isSupportedByPerfettoEditor(file)) {
        return true
      }

      val formats = mutableListOf<ProfilerFormat>()
      if (StudioFlags.PROFILER_CALLSTACK_SAMPLE_TRACE_IN_EDITOR.get()) {
        formats.add(ProfilerFormat.SimplePerfTrace)
      }
      if (StudioFlags.PROFILER_HEAP_DUMP_TRACE_IN_EDITOR.get()) {
        formats.add(ProfilerFormat.Hprof)
      }
      if (StudioFlags.PROFILER_NATIVE_ALLOCATIONS_TRACE_IN_EDITOR.get()) {
        formats.add(ProfilerFormat.NativeAllocations)
      }
      if (StudioFlags.PROFILER_JAVA_KOTLIN_ALLOCATIONS_LEGACY_TRACE_IN_EDITOR.get()) {
        formats.add(ProfilerFormat.JavaKotlinLegacyAllocations)
      }

      if (formats.isEmpty()) {
        return false
      }

      val lazyTraceType = getLazyTraceType(file)

      return try {
        formats.any { it.matches(file.extension, lazyTraceType) }
      } catch (e: Exception) {
        // Fallback to false if file cannot be read (e.g. FileNotFoundException)
        log.warn("Error checking file support: ${file.path}", e)
        false
      }
    }
  }
}
