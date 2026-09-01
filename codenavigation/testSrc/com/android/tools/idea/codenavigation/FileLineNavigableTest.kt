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
package com.android.tools.idea.codenavigation

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FileLineNavigableTest {

  private lateinit var testDisposable: Disposable
  private lateinit var mockApp: Application
  private lateinit var mockVfsManager: VirtualFileManager
  private lateinit var mockLfs: LocalFileSystem

  @Before
  fun setUp() {
    testDisposable = Disposer.newDisposable()
    mockApp = mock(Application::class.java)
    mockVfsManager = mock(VirtualFileManager::class.java)
    mockLfs = mock(LocalFileSystem::class.java)

    `when`(mockApp.getService(VirtualFileManager::class.java)).thenReturn(mockVfsManager)
    `when`(mockApp.getService(LocalFileSystem::class.java)).thenReturn(mockLfs)
    `when`(mockVfsManager.getFileSystem(anyString())).thenReturn(mockLfs)

    ApplicationManager.setApplication(mockApp, testDisposable)
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun testLookUpFileInProject() {
    val filePath = "/project/src/InProject.kt"
    val mockFile = mock(VirtualFile::class.java)
    val mockProject = mock(Project::class.java)
    val mockFileIndex = mock(ProjectFileIndex::class.java)
    val mockRangeMarkerFactory = mock(com.intellij.openapi.editor.LazyRangeMarkerFactory::class.java)

    val lfs = LocalFileSystem.getInstance()
    `when`(lfs.findFileByPath(filePath)).thenReturn(mockFile)
    `when`(mockProject.getService(ProjectFileIndex::class.java)).thenReturn(mockFileIndex)
    `when`(mockProject.getService(com.intellij.openapi.editor.LazyRangeMarkerFactory::class.java)).thenReturn(mockRangeMarkerFactory)
    `when`(mockFileIndex.isInProject(mockFile)).thenReturn(true)

    val navigable = FileLineNavigable(mockProject)
    val location = CodeLocation.Builder("com.example.InProject").setFileName(filePath).setLineNumber(42).build()

    val result = navigable.lookUp(location, null)
    assertThat(result).isInstanceOf(OpenFileDescriptor::class.java)
    val descriptor = result as OpenFileDescriptor
    assertThat(descriptor.file).isEqualTo(mockFile)
    assertThat(descriptor.line).isEqualTo(42)
  }

  @Test
  fun testLookUpFileNotInProject() {
    val filePath = "/other/NotInProject.kt"
    val mockFile = mock(VirtualFile::class.java)
    val mockProject = mock(Project::class.java)
    val mockFileIndex = mock(ProjectFileIndex::class.java)

    val lfs = LocalFileSystem.getInstance()
    `when`(lfs.findFileByPath(filePath)).thenReturn(mockFile)
    `when`(mockProject.getService(ProjectFileIndex::class.java)).thenReturn(mockFileIndex)
    `when`(mockFileIndex.isInProject(mockFile)).thenReturn(false)

    val navigable = FileLineNavigable(mockProject)
    val location = CodeLocation.Builder("com.example.NotInProject").setFileName(filePath).setLineNumber(42).build()

    val result = navigable.lookUp(location, null)
    assertThat(result).isNull()
  }

  @Test
  fun testLookUpInvalidLocationOrMissingFile() {
    val mockProject = mock(Project::class.java)
    val navigable = FileLineNavigable(mockProject)

    // Missing file name
    val locationNoFile = CodeLocation.Builder("com.example.NoFile").setLineNumber(10).build()
    assertThat(navigable.lookUp(locationNoFile, null)).isNull()

    // Invalid line number
    val locationInvalidLine =
      CodeLocation.Builder("com.example.InvalidLine")
        .setFileName("/some/path/File.kt")
        .setLineNumber(CodeLocation.INVALID_LINE_NUMBER)
        .build()
    assertThat(navigable.lookUp(locationInvalidLine, null)).isNull()

    // Non-existent file path
    val locationNonExistent =
      CodeLocation.Builder("com.example.NonExistent").setFileName("/non/existent/path/File.kt").setLineNumber(10).build()
    val lfs = LocalFileSystem.getInstance()
    `when`(lfs.findFileByPath("/non/existent/path/File.kt")).thenReturn(null)
    assertThat(navigable.lookUp(locationNonExistent, null)).isNull()

    // UNC file path
    val uncPath = "\\\\server\\share\\File.kt"
    val locationUnc = CodeLocation.Builder("com.example.Unc").setFileName(uncPath).setLineNumber(10).build()
    assertThat(navigable.lookUp(locationUnc, null)).isNull()
  }
}
