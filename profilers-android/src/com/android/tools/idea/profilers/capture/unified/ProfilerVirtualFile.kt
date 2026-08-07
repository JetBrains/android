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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.profilers.taskbased.common.icons.TaskIconUtils
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import java.io.InputStream
import java.io.OutputStream

/**
 * An in-memory [VirtualFile] representing an active or completed Live Profiler Task in the main editor area.
 *
 * Unlike offline capture files (e.g. Perfetto traces, HPROF heap dumps) that are loaded from physical files on disk, live tasks stream data
 * via continuous bi-directional transport pipelines. This virtual file allows the IDE's [FileEditorManager] to host the live profiler UI in
 * an editor tab without disk I/O or extension conflicts.
 *
 * @property sessionId The unique identifier of the live session.
 * @property taskType The type of profiler task being performed (e.g. [ProfilerTaskType.LIVE_VIEW],
 *   [ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS]).
 * @property taskName The human-readable title of the task displayed on the editor tab.
 */
class ProfilerVirtualFile(val sessionId: Long, val taskType: ProfilerTaskType, private val taskName: String) : VirtualFile() {

  override fun getName(): String = taskName

  override fun getPresentableName(): String = taskName

  override fun getFileSystem(): VirtualFileSystem = DummyFileSystem.getInstance()

  override fun getPath(): String = taskName

  override fun isWritable(): Boolean = false

  override fun isDirectory(): Boolean = false

  override fun isValid(): Boolean = true

  override fun getParent(): VirtualFile? = null

  override fun getChildren(): Array<VirtualFile> = emptyArray()

  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
    throw UnsupportedOperationException()
  }

  override fun contentsToByteArray(): ByteArray = ByteArray(0)

  override fun getTimeStamp(): Long = 0L

  override fun getLength(): Long = 0L

  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

  override fun getInputStream(): InputStream {
    throw UnsupportedOperationException()
  }

  override fun getFileType() =
    object : FakeFileType() {
      override fun isMyFileType(file: VirtualFile) = file is ProfilerVirtualFile

      override fun getName() = "ProfilerCapture"

      override fun getDescription() = "Profiler Capture"

      override fun getIcon() = TaskIconUtils.getTaskIcon(taskType)
    }
}
