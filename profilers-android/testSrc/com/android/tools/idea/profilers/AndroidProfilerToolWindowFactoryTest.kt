package com.android.tools.idea.profilers

import com.android.tools.idea.sdk.AndroidOrApkFacetChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [AndroidProfilerToolWindowFactory]
 */
class AndroidProfilerToolWindowFactoryTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Android Profiler" }
      ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidOrApkFacetChecker::class.qualifiedName)
  }
}