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
package org.jetbrains.android.uipreview

import com.android.tools.rendering.security.EP_NAME
import com.android.tools.rendering.security.RenderSecurityManagerOverrides
import com.intellij.mock.MockApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerExtension
import java.io.File
import org.junit.Assert.fail
import org.junit.Test

class StudioRenderSandboxTest {
  /** [RenderSecurityManagerOverrides] allows ASWB to override the "checkProperty" check. */
  @Test
  fun `check render security manager overrides`() {
    val disposable = Disposer.newDisposable()
    try {
      val app = MockApplication(disposable)
      ApplicationManager.setApplication(app, disposable)
      @Suppress("UnstableApiUsage")
      app.extensionArea.registerExtensionPoint(
        EP_NAME.name,
        RenderSecurityManagerOverrides::class.java.name,
        ExtensionPoint.Kind.INTERFACE,
        false,
      )

      val sandbox = StudioRenderSandbox(null, null, null)
      try {
        sandbox.checkPropertyAccess()
        fail("Expected SecurityException")
      } catch (_: SecurityException) {}

      // Install extension
      app.registerExtension(
        EP_NAME,
        object : RenderSecurityManagerOverrides {
          override fun allowsPropertiesAccess(): Boolean = true

          override fun allowsLibraryLinking(lib: String): Boolean = false
        },
        disposable,
      )
      sandbox.checkPropertyAccess()
    } finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `check file read allowed in project path`() {
    val projectDir = File("/path/to/project").absolutePath
    val sandbox = StudioRenderSandbox(null, projectDir, null)
    sandbox.checkFileRead(File(projectDir, "file.txt").absolutePath)
  }

  @Test
  fun `check file read denied outside project path`() {
    val projectDir = File("/path/to/project").absolutePath
    val sandbox = StudioRenderSandbox(null, projectDir, null)
    try {
      sandbox.checkFileRead(File("/path/to/other/file.txt").absolutePath)
      fail("Expected SecurityException")
    } catch (_: SecurityException) {}
  }

  @Test
  fun `check file write allowed in temp dir`() {
    val sandbox = StudioRenderSandbox(null, null, null)
    val tempDir = System.getProperty("java.io.tmpdir")
    sandbox.checkFileWrite(File(tempDir, "file.txt").absolutePath)
  }

  @Test
  fun `check file write denied outside temp dir`() {
    val sandbox = StudioRenderSandbox(null, null, null)
    try {
      sandbox.checkFileWrite(File("/path/to/project/file.txt").absolutePath)
      fail("Expected SecurityException")
    } catch (_: SecurityException) {}
  }
}
