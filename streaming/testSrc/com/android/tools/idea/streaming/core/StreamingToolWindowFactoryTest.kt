package com.android.tools.idea.streaming.core

import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [StreamingToolWindowFactory]
 */
class StreamingToolWindowFactoryTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun isLibraryToolWindow() {
    val toolWindow = LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Running Devices" }
                     ?: fail("Tool window not found")

    Assert.assertEquals(toolWindow.librarySearchClass, AndroidEnvironmentChecker::class.qualifiedName)
  }
}