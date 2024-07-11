/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.rendering.security

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.fail
import org.junit.Test

class PreCheckRenderSandboxDelegateTest {
  private fun assertThrowsSecurityException(block: () -> Unit) {
    try {
      block()
      fail("Expected SecurityException")
    } catch (_: SecurityException) {} catch (t: Throwable) {
      fail("Unexpected exception: $t")
    }
  }

  @Test
  fun `check runs and short-circuits delegate call`() {
    val isEnabled = AtomicBoolean(false)
    val renderSandbox = PreCheckRenderSandboxDelegate(DenyAllRenderSandbox, check = { isEnabled.get() })
    renderSandbox.checkConnection()
    renderSandbox.checkEnvAccess()
    renderSandbox.checkPropertyAccess()
    renderSandbox.checkSystemExit()
    renderSandbox.checkProcessExec()
    renderSandbox.checkSystemIoSet()
    renderSandbox.checkClassLoad("a.b.C")
    renderSandbox.checkResourceLoad("resource")
    renderSandbox.checkProcessExec()
    renderSandbox.checkFileWrite("/file")
    renderSandbox.checkFileRead("/file")
    renderSandbox.checkLoadLibrary("library")

    isEnabled.set(true)
    assertThrowsSecurityException { renderSandbox.checkConnection() }
    assertThrowsSecurityException { renderSandbox.checkEnvAccess() }
    assertThrowsSecurityException { renderSandbox.checkPropertyAccess() }
    assertThrowsSecurityException { renderSandbox.checkSystemExit() }
    assertThrowsSecurityException { renderSandbox.checkProcessExec() }
    assertThrowsSecurityException { renderSandbox.checkSystemIoSet() }
    assertThrowsSecurityException { renderSandbox.checkClassLoad("a.b.C") }
    assertThrowsSecurityException { renderSandbox.checkResourceLoad("resource") }
    assertThrowsSecurityException { renderSandbox.checkProcessExec() }
    assertThrowsSecurityException { renderSandbox.checkFileWrite("/file") }
    assertThrowsSecurityException { renderSandbox.checkFileRead("/file") }
    assertThrowsSecurityException { renderSandbox.checkLoadLibrary("library") }
  }
}
