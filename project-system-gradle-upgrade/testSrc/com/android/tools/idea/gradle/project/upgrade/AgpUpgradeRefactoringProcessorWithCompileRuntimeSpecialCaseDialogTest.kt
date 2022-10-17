/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_PAST
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Test
import java.lang.reflect.Field
import javax.swing.Action

class AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialogTest : HeavyPlatformTestCase() {
  private val isPreviewUsagesField: Field = BaseRefactoringProcessor::class.java.getDeclaredField("myIsPreviewUsages")
  private val isPreviewUsagesFieldAccessible = isPreviewUsagesField.isAccessible

  override fun setUp() {
    super.setUp()
    isPreviewUsagesField.isAccessible = true
  }

  override fun tearDown() {
    isPreviewUsagesField.isAccessible = isPreviewUsagesFieldAccessible
    super.tearDown()
    checkNoUndisposedDialogs()
  }

  private fun AgpUpgradeRefactoringProcessor.getCompileRuntimeProcessor() =
    componentRefactoringProcessors.firstNotNullOfOrNull { it as? CompileRuntimeConfigurationRefactoringProcessor }!!

  private fun AgpUpgradeRefactoringProcessor.setCompileRuntimeIsAlwaysNoOpForProject(value: Boolean) {
    this.getCompileRuntimeProcessor().isAlwaysNoOpForProject = value
  }

  @Test
  fun testDialogSetsEnabled() {
    val irrelevant = setOf(IRRELEVANT_PAST, IRRELEVANT_FUTURE)
    val optional = setOf(OPTIONAL_INDEPENDENT, OPTIONAL_CODEPENDENT)
    val mandatory = setOf(MANDATORY_INDEPENDENT, MANDATORY_CODEPENDENT)
    fun checkInitialConsistency(p : AgpUpgradeComponentRefactoringProcessor) {
      // isEnabled defaults to any non-irrelevant, listed explicitly here so that we can modify things if more necessities are added
      when (p.isEnabled) {
        true -> assertTrue(p.commandName, optional.union(mandatory).contains(p.necessity()))
        false -> assertTrue(p.commandName, irrelevant.contains(p.necessity()))
      }
    }
    fun checkFinalConsistency(p : AgpUpgradeComponentRefactoringProcessor) {
      // the dialog sets isEnabled true on only the CompileRuntimeConfiguration processor.
      when (p.isEnabled) {
        true -> assertTrue(p.commandName, p is CompileRuntimeConfigurationRefactoringProcessor)
        false -> assertTrue(p.commandName, p !is CompileRuntimeConfigurationRefactoringProcessor)
      }
    }
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.componentRefactoringProcessors.forEach { checkInitialConsistency(it) }
    registerDialogDisposable(AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(processor, processor.getCompileRuntimeProcessor()))
    processor.componentRefactoringProcessors.forEach { checkFinalConsistency(it) }
  }

  @Test
  fun testOKActionIsNotPreview() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setCompileRuntimeIsAlwaysNoOpForProject(false)
    val compileRuntimeProcessor = processor.getCompileRuntimeProcessor()
    val dialog = AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(processor, compileRuntimeProcessor)
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testCancelActionIsNotPreview() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setCompileRuntimeIsAlwaysNoOpForProject(false)
    val compileRuntimeProcessor = processor.getCompileRuntimeProcessor()
    val dialog = AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(processor, compileRuntimeProcessor)
    dialog.doCancelAction()
    assertFalse(dialog.isOK)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testPreviewActionIsPreview() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setCompileRuntimeIsAlwaysNoOpForProject(false)
    val compileRuntimeProcessor = processor.getCompileRuntimeProcessor()
    val dialog = AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(processor, compileRuntimeProcessor)
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Show Usages" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testOneArgumentConstructor() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(processor))
    val field = AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog::class.java.getDeclaredField("myCompileRuntimeProcessor")
    field.isAccessible = true
    assertEquals(processor.getCompileRuntimeProcessor(), field.get(dialog))
  }
}
