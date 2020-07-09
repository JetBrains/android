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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_PAST
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Test
import java.lang.reflect.Field
import javax.swing.Action

class AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialogTest : HeavyPlatformTestCase() {
  private val isPreviewUsagesField: Field = BaseRefactoringProcessor::class.java.getDeclaredField("myIsPreviewUsages")
  private val isPreviewUsagesFieldAccessible = isPreviewUsagesField.isAccessible

  override fun setUp() {
    super.setUp()
    isPreviewUsagesField.isAccessible = true
  }

  override fun tearDown() {
    isPreviewUsagesField.isAccessible = isPreviewUsagesFieldAccessible
    super.tearDown()
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
      // the dialog sets isEnabled true only on mandatory necessities.
      when (p.isEnabled) {
        true -> assertTrue(p.commandName, mandatory.contains(p.necessity()))
        false -> assertTrue(p.commandName, irrelevant.union(optional).contains(p.necessity()))
      }
    }
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    (processor.componentRefactoringProcessors + processor.classpathRefactoringProcessor).forEach { checkInitialConsistency(it) }
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    (processor.componentRefactoringProcessors + processor.classpathRefactoringProcessor).forEach { checkFinalConsistency(it) }
    Disposer.dispose(dialog.disposable)
  }

  @Test
  fun testHasComboBoxTo420Alpha05() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0-alpha05"))
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(1, comboBoxes)
    Disposer.dispose(dialog.disposable)
  }

  @Test
  fun testHasComboBoxFrom420Alpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.2.0-alpha04"), GradleVersion.parse("4.2.0"))
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(1, comboBoxes)
    Disposer.dispose(dialog.disposable)
  }

  @Test
  fun testHasNoComboBoxToAlpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0-alpha04"))
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
    Disposer.dispose(dialog.disposable)
  }

  @Test
  fun testHasNoComboBoxFrom420Alpha05() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.2.0-alpha05"), GradleVersion.parse("4.2.0"))
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
    Disposer.dispose(dialog.disposable)
  }

  @Test
  fun testComboBoxDefaultsToAccept() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    assertEquals(ACCEPT_NEW_DEFAULT, comboBox.model.selectedItem)
    Disposer.dispose(dialog.disposable)
  }

  @Test
  fun testDefaultOKActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    val java8DefaultProcessor = processor.componentRefactoringProcessors.firstIsInstance<Java8DefaultRefactoringProcessor>()
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertEquals(ACCEPT_NEW_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testOKActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    val java8DefaultProcessor = processor.componentRefactoringProcessors.firstIsInstance<Java8DefaultRefactoringProcessor>()
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    java8DefaultProcessor.noLanguageLevelAction = ACCEPT_NEW_DEFAULT
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    comboBox.selectedItem = INSERT_OLD_DEFAULT
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testCancelActionLeavesNoLanguageLevelActionAlone() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    val java8DefaultProcessor = processor.componentRefactoringProcessors.firstIsInstance<Java8DefaultRefactoringProcessor>()
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    dialog.doCancelAction()
    assertFalse(dialog.isOK)
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testDefaultPreviewActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    val java8DefaultProcessor = processor.componentRefactoringProcessors.firstIsInstance<Java8DefaultRefactoringProcessor>()
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Preview" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertEquals(ACCEPT_NEW_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testPreviewActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("4.2.0"))
    val java8DefaultProcessor = processor.componentRefactoringProcessors.firstIsInstance<Java8DefaultRefactoringProcessor>()
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    java8DefaultProcessor.noLanguageLevelAction = ACCEPT_NEW_DEFAULT
    val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(processor)
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    comboBox.selectedItem = INSERT_OLD_DEFAULT
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Preview" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertEquals(INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }
}