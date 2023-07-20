package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import com.jetbrains.rd.generator.nova.fail
import org.junit.Rule
import org.junit.Test

/** Tests for [AppInspectionToolWindowFactory] */
class AppInspectionToolWindowFactoryTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "App Inspection" }
        ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidEnvironmentChecker::class.qualifiedName)
  }
}
