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
package com.android.tools.idea.tests.gui.projectstructure

import com.android.tools.idea.gradle.variant.view.BuildVariantView
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class ModuleVariantConflictTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  @Throws(Exception::class)
  fun displayConflict() {
    val ide = guiTest.importModuleVariantConflictsApplication()
    // app and app2 depends on different variants of mylibrary
    assertThat(ide.buildVariantsWindow.jTableFixture.contents()).isEqualTo(arrayOf(
      arrayOf("Module: 'SimpleVariantConflictApp.app'", "flv1Debug (default)"),
      arrayOf("Module: 'SimpleVariantConflictApp.app2'", "flv2Debug (default)"),
      arrayOf("Module: 'SimpleVariantConflictApp.mylibrary'", "flv1Debug (default)")))
    ide.buildVariantsWindow.getModuleCell("SimpleVariantConflictApp.app").background().requireNotEqualTo(BuildVariantView.CONFLICT_CELL_BACKGROUND)
    ide.buildVariantsWindow.getModuleCell("SimpleVariantConflictApp.app2").background().requireEqualTo(BuildVariantView.CONFLICT_CELL_BACKGROUND)
    // Resolve app2 conflict by changing the variant of mylibrary to match app2
    ide.buildVariantsWindow.selectVariantForModule("SimpleVariantConflictApp.mylibrary", "flv2Debug")
    guiTest.waitForBackgroundTasks()
    ide.buildVariantsWindow.getModuleCell("SimpleVariantConflictApp.app").background().requireEqualTo(BuildVariantView.CONFLICT_CELL_BACKGROUND)
    ide.buildVariantsWindow.getModuleCell("SimpleVariantConflictApp.app2").background().requireNotEqualTo(BuildVariantView.CONFLICT_CELL_BACKGROUND)
  }
}