/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test
import javax.swing.JEditorPane

class AgpUpgradeRefactoringProcessorCannotUpgradeDialogTest : HeavyPlatformTestCase() {

  override fun tearDown() {
    JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
    super.tearDown()
    checkNoUndisposedDialogs()
  }

  @Test
  fun testCannotUpgradeDialogNoBuildFile() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersions.latestKnown)
    assertTrue(processor.blockProcessorExecution())
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorCannotUpgradeDialog(processor))
    val editorPanes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), JEditorPane::class.java)
    assertSize(1, editorPanes)
    assertTrue(editorPanes[0].text.contains("failed to upgrade this project"))
    assertTrue(editorPanes[0].text.contains("no way"))
    assertTrue(editorPanes[0].text.contains("Upgrade AGP dependency from 4.1.0 to ${AgpVersions.latestKnown}"))
    assertTrue(editorPanes[0].text.contains("Cannot find AGP version in build files."))
  }
}