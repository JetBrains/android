package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.sdk.AndroidFacetChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [ResourceExplorerToolFactory]
 */
class ResourceExplorerToolFactoryTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Resources Explorer" }
      ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidFacetChecker::class.qualifiedName)
  }
}