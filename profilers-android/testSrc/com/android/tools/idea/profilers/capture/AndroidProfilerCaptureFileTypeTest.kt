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
package com.android.tools.idea.profilers.capture

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.capture.unified.UnifiedProfilerEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.mock

class AndroidProfilerCaptureFileTypeTest {

  private val mockProject: Project = mock()
  private val mockFile: VirtualFile = mock()
  private val mockFileEditorManager: FileEditorManager = mock()

  // We spy on the real providers.
  // We will force the 'accept' outcome to test the FileType's dispatch logic specifically,
  // preventing failures due to static dependencies (FileTypeManager) or IO (content parsing)
  // that reside inside the real accept() implementations.
  private val mockLegacyProvider: FileEditorProvider = spy(AndroidProfilerCaptureEditorProvider())
  private val mockUnifiedProvider: FileEditorProvider = spy(UnifiedProfilerEditorProvider())

  private lateinit var captureFileType: AndroidProfilerCaptureFileType

  @Before
  fun setUp() {
    // Create a spy of the abstract class to test the logic.
    captureFileType = spy(object : AndroidProfilerCaptureFileType() {
      override fun getName() = "TestCapture"
      override fun getDescription() = "Test Capture File Type"
      override fun getDefaultExtension() = "trace"
    })
    whenever(mockFile.isValid).thenReturn(true)
    whenever(mockFile.isInLocalFileSystem).thenReturn(true)
    whenever(mockFile.extension).thenReturn("pftrace")
  }

  @Test
  fun openFile_whenLegacyProviderAccepts_returnsTrue() {
    // Arrange: Unified provider rejects, but legacy provider accepts.
    StudioFlags.PROFILER_SYSTEM_TRACE_IN_EDITOR.override(false)
    // TODO(b/473757417): Read real files instead of mocking the response
    doReturn(false).whenever(mockUnifiedProvider).accept(any(), any())
    doReturn(true).whenever(mockLegacyProvider).accept(any(), any())

    doReturn(mock<FileEditor>())
      .whenever(mockLegacyProvider).createEditor(any(), any())

    // Act: Attempt to open the file.
    val result = captureFileType.openFileInAssociatedApplication(
      mockProject, mockFile, mockLegacyProvider, mockUnifiedProvider, mockFileEditorManager
    )

    // Assert: The method should return true.
    assertTrue(result)

    // Verify Unified was checked but rejected
    verify(mockUnifiedProvider).accept(mockProject, mockFile)

    // Verify Legacy was checked and used
    verify(mockLegacyProvider).accept(mockProject, mockFile)
    verify(mockLegacyProvider).createEditor(mockProject, mockFile)
    // Verify the file manager was not used directly.
    verify(mockFileEditorManager, never()).openFile(any(), anyBoolean())
  }

  @Test
  fun openFile_whenUnifiedProviderAccepts_returnsTrue() {
    // Arrange: Legacy provider rejects, but unified provider accepts.
    StudioFlags.PROFILER_SYSTEM_TRACE_IN_EDITOR.override(true)
    // TODO(b/473757417): Read real files instead of mocking the response
    doReturn(true).whenever(mockUnifiedProvider).accept(any(), any())
    doReturn(false).whenever(mockLegacyProvider).accept(any(), any())

    // Act: Attempt to open the file.
    val result = captureFileType.openFileInAssociatedApplication(
      mockProject, mockFile, mockLegacyProvider, mockUnifiedProvider, mockFileEditorManager)

    // Assert: The method should return true.
    assertTrue(result)
    // Verify the unified provider was consulted first.
    verify(mockUnifiedProvider).accept(mockProject, mockFile)
    // Verify the legacy provider's createEditor was NOT called.
    verify(mockLegacyProvider, never()).createEditor(any(), any())
    // Verify the file was opened using the FileEditorManager.
    verify(mockFileEditorManager).openFile(mockFile, true)
  }

  @Test
  fun openFile_whenBothProvidersReject_returnsFalse() {
    // Arrange: Both providers will reject the file.
    doReturn(false).whenever(mockUnifiedProvider).accept(any(), any())
    doReturn(false).whenever(mockLegacyProvider).accept(any(), any())

    // Act: Attempt to open the file.
    val result = captureFileType.openFileInAssociatedApplication(
      mockProject, mockFile, mockLegacyProvider, mockUnifiedProvider, mockFileEditorManager)

    // Assert: The method should return false.
    assertFalse(result)
    // Verify neither provider's editor creation/opening logic was called.
    verify(mockLegacyProvider, never()).createEditor(any(), any())
    verify(mockFileEditorManager, never()).openFile(any(), anyBoolean())
  }

  @After
  fun tearDown() {
    StudioFlags.PROFILER_SYSTEM_TRACE_IN_EDITOR.clearOverride()
  }
}
