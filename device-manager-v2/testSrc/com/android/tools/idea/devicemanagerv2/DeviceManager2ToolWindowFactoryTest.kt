package com.android.tools.idea.devicemanagerv2

import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

/** Tests for [DeviceManager2ToolWindowFactory] */
class DeviceManager2ToolWindowFactoryTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find {
        it.id == "Device Manager 2"
      } ?: throw AssertionError("Tool window not found")

    Assert.assertEquals(
      toolWindow.librarySearchClass,
      AndroidEnvironmentChecker::class.qualifiedName
    )
  }
}
