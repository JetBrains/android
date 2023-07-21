package com.android.tools.idea.device.explorer

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
    val toolWindow = LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Device Explorer" }
                     ?: throw AssertionError("Tool window not found")

    assertThat(toolWindow.librarySearchClass).isEqualTo(AndroidEnvironmentChecker::class.qualifiedName)
  }
}