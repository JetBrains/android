/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.file.explorer.toolwindow

import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [DeviceExplorerToolWindowFactory]
 */
class DeviceExplorerToolWindowFactoryTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun isLibraryToolWindow() {
    val toolWindow = LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Device File Explorer" }
                     ?: throw AssertionError("Tool window not found")

    assertThat(toolWindow.librarySearchClass).isEqualTo(AndroidEnvironmentChecker::class.qualifiedName)
  }
}