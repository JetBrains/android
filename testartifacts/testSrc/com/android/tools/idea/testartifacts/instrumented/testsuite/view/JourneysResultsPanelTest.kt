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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq

@RunsInEdt
class JourneysResultsPanelTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val edtRule = EdtRule()
  private val disposableRule = DisposableRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(edtRule).around(disposableRule)

  @Test
  fun openImageInEditorRefreshesVfsIfFileNotFound() {
    val mockFileEditorManager = mock(FileEditorManager::class.java)
    projectRule.project.replaceService(FileEditorManager::class.java, mockFileEditorManager, disposableRule.disposable)

    // Create a new directory and file via standard Java IO.
    // With a heavy fixture (onDisk), the VFS will not automatically pick up changes
    // made via IO in a new, un-indexed directory.
    val basePath = projectRule.project.basePath!!
    val newDir = File(basePath, "io_created_dir")
    newDir.mkdir()
    val imageFile = File(newDir, "external_image.png")
    imageFile.createNewFile()

    try {
      // Verify that the VFS cache currently does NOT know about this file.
      assertThat(VfsUtil.findFileByIoFile(imageFile, false)).isNull()

      val panel = JourneysResultsPanel(projectRule.project)

      // This call should succeed because the implementation uses refreshIfNeeded = true
      panel.openImageInEditor(imageFile)

      // Verify the editor was opened with the correct file.
      val descriptorCaptor = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
      verify(mockFileEditorManager).openEditor(descriptorCaptor.capture(), eq(true))
      assertThat(descriptorCaptor.value.file.path).isEqualTo(imageFile.absolutePath.replace(File.separatorChar, '/'))
    } finally {
      imageFile.delete()
      newDir.delete()
    }
  }

  private fun createTempScreenshotFile(): File {
    val bufferedImage =
      BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
        with(createGraphics()) {
          color = Color(0x012345)
          fillRect(0, 0, 100, 100)
        }
      }
    return File.createTempFile("screenshot", ".png").apply { ImageIO.write(bufferedImage, "png", this) }
  }
}
