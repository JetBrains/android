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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.IRRELEVANT_PAST
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test
import java.lang.reflect.Field
import javax.swing.Action
import javax.swing.JEditorPane

class AgpUpgradeRefactoringProcessorDialogTest : HeavyPlatformTestCase() {
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

  private fun AgpUpgradeRefactoringProcessor.getJava8DefaultRefactoringProcessor() =
    componentRefactoringProcessors.firstNotNullOfOrNull { it as? Java8DefaultRefactoringProcessor }!!

  private fun AgpUpgradeRefactoringProcessor.setJava8DefaultIsAlwaysNoOpForProject(value: Boolean) {
    this.getJava8DefaultRefactoringProcessor().isAlwaysNoOpForProject = value
  }

  private fun AgpUpgradeRefactoringProcessor.getR8FullModeDefaultRefactoringProcessor() =
    componentRefactoringProcessors.firstNotNullOfOrNull { it as? R8FullModeDefaultRefactoringProcessor }!!

  private fun AgpUpgradeRefactoringProcessor.setR8FullModeDefaultIsAlwaysNoOpForProject(value: Boolean) {
    this.getR8FullModeDefaultRefactoringProcessor().isAlwaysNoOpForProject = value
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
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.componentRefactoringProcessors.forEach { checkInitialConsistency(it) }
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    processor.componentRefactoringProcessors.forEach { checkFinalConsistency(it) }
  }

  @Test
  fun testDialogWithProcessorStateDoesNotSetEnabled() {
    val irrelevant = setOf(IRRELEVANT_PAST, IRRELEVANT_FUTURE)
    val optional = setOf(OPTIONAL_INDEPENDENT, OPTIONAL_CODEPENDENT)
    val mandatory = setOf(MANDATORY_INDEPENDENT, MANDATORY_CODEPENDENT)
    fun checkConsistency(p : AgpUpgradeComponentRefactoringProcessor) {
      // isEnabled defaults to any non-irrelevant, listed explicitly here so that we can modify things if more necessities are added
      when (p.isEnabled) {
        true -> assertTrue(p.commandName, optional.union(mandatory).contains(p.necessity()))
        false -> assertTrue(p.commandName, irrelevant.contains(p.necessity()))
      }
    }
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.componentRefactoringProcessors.forEach { checkConsistency(it) }
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    processor.componentRefactoringProcessors.forEach { checkConsistency(it) }
  }

  @Test
  fun testHasNoWarningTextIfFilesNotChanged() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0-alpha05"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val editorPanes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), JEditorPane::class.java)
    assertSize(1, editorPanes)
    assertFalse(editorPanes[0].text.contains("<b>Warning</b>:"))
  }

  @Test
  fun testHasWarningTextIfFilesChanged() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0-alpha05"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), true))
    val editorPanes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), JEditorPane::class.java)
    assertSize(1, editorPanes)
    assertTrue(editorPanes[0].text.contains("<b>Warning</b>:"))
  }

  @Test
  fun testHasComboBoxTo420Alpha05() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0-alpha05"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(1, comboBoxes)
  }

  @Test
  fun testHasComboBoxTo800Alpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0-alpha04"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(1, comboBoxes)
  }

  @Test
  fun testHasTwoComboBoxesFrom410To800Alpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("8.0.0-alpha04"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(2, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxTo420Alpha05IfNoOp() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0-alpha05"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(true)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxTo800Alpha04IfNoOp() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0-alpha04"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(true)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasComboBoxFrom420Alpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0-alpha04"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(1, comboBoxes)
  }

  @Test
  fun testHasComboBoxFrom740() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.4.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(1, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxFrom420Alpha04IfNoOp() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0-alpha04"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(true)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxFrom800Alpha03IfNoOp() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("8.0.0-alpha03"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(true)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxFrom420Alpha04IfDisabledWithState() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0-alpha04"), AgpVersion.parse("4.2.0"))
    processor.getJava8DefaultRefactoringProcessor().isEnabled = false
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxFrom800Alpha03IfDisabledWithState() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("8.0.0-alpha03"), AgpVersion.parse("8.0.0"))
    processor.getR8FullModeDefaultRefactoringProcessor().isEnabled = false
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxToAlpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0-alpha04"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxTo740() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("7.4.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxFrom420Alpha05() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0-alpha05"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testHasNoComboBoxFrom800Alpha04() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("8.0.0-alpha04"), AgpVersion.parse("8.0.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBoxes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), ComboBox::class.java)
    assertSize(0, comboBoxes)
  }

  @Test
  fun testJava8ComboBoxDefaultsToAccept() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    assertEquals(NoLanguageLevelAction.ACCEPT_NEW_DEFAULT, comboBox.model.selectedItem)
  }

  @Test
  fun testR8FullModeComboBoxDefaultsToAccept() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    assertEquals(NoPropertyPresentAction.ACCEPT_NEW_DEFAULT, comboBox.model.selectedItem)
  }

  @Test
  fun testJava8ComboBoxDefaultsToProcessorValueWithState() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.getJava8DefaultRefactoringProcessor().noLanguageLevelAction = NoLanguageLevelAction.INSERT_OLD_DEFAULT
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, comboBox.model.selectedItem)
  }

  @Test
  fun testR8FullModeComboBoxDefaultsToProcessorValueWithState() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    processor.getR8FullModeDefaultRefactoringProcessor().noPropertyPresentAction = NoPropertyPresentAction.INSERT_OLD_DEFAULT
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, comboBox.model.selectedItem)
  }

  @Test
  fun testDefaultOKActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val java8DefaultProcessor = processor.getJava8DefaultRefactoringProcessor()
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, java8DefaultProcessor, processor.getR8FullModeDefaultRefactoringProcessor(), false))
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertEquals(NoLanguageLevelAction.ACCEPT_NEW_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testDefaultOKActionSetsNoPropertyPresentAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val r8FullModeDefaultProcessor = processor.getR8FullModeDefaultRefactoringProcessor()
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(), r8FullModeDefaultProcessor, false))
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertEquals(NoPropertyPresentAction.ACCEPT_NEW_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testOKActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val java8DefaultProcessor = processor.getJava8DefaultRefactoringProcessor()
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    java8DefaultProcessor.noLanguageLevelAction = NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, java8DefaultProcessor, processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    comboBox.selectedItem = NoLanguageLevelAction.INSERT_OLD_DEFAULT
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testOKActionSetsNoPropertyPresentAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val r8FullModeDefaultProcessor = processor.getR8FullModeDefaultRefactoringProcessor()
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    r8FullModeDefaultProcessor.noPropertyPresentAction = NoPropertyPresentAction.ACCEPT_NEW_DEFAULT
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(), r8FullModeDefaultProcessor, false))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    comboBox.selectedItem = NoPropertyPresentAction.INSERT_OLD_DEFAULT
    dialog.doOKAction()
    assertTrue(dialog.isOK)
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testCancelActionLeavesNoLanguageLevelActionAlone() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val java8DefaultProcessor = processor.getJava8DefaultRefactoringProcessor()
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, java8DefaultProcessor, processor.getR8FullModeDefaultRefactoringProcessor(), false))
    dialog.doCancelAction()
    assertFalse(dialog.isOK)
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testCancelActionLeavesNoPropertyPresentActionAlone() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val r8FullModeDefaultProcessor = processor.getR8FullModeDefaultRefactoringProcessor()
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(), r8FullModeDefaultProcessor, false))
    dialog.doCancelAction()
    assertFalse(dialog.isOK)
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    assertFalse(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testDefaultPreviewActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val java8DefaultProcessor = processor.getJava8DefaultRefactoringProcessor()
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, java8DefaultProcessor, processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Show Usages" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertEquals(NoLanguageLevelAction.ACCEPT_NEW_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testDefaultPreviewActionSetsNoPropertyPresentAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val r8FullModeDefaultProcessor = processor.getR8FullModeDefaultRefactoringProcessor()
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(), r8FullModeDefaultProcessor, false))
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Show Usages" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertEquals(NoPropertyPresentAction.ACCEPT_NEW_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testPreviewActionSetsNoLanguageLevelAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    val java8DefaultProcessor = processor.getJava8DefaultRefactoringProcessor()
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    java8DefaultProcessor.noLanguageLevelAction = NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, java8DefaultProcessor, processor.getR8FullModeDefaultRefactoringProcessor(), false))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    comboBox.selectedItem = NoLanguageLevelAction.INSERT_OLD_DEFAULT
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Show Usages" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertEquals(NoLanguageLevelAction.INSERT_OLD_DEFAULT, java8DefaultProcessor.noLanguageLevelAction)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testPreviewActionSetsNoPropertyPresentAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.setR8FullModeDefaultIsAlwaysNoOpForProject(false)
    val r8FullModeDefaultProcessor = processor.getR8FullModeDefaultRefactoringProcessor()
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    r8FullModeDefaultProcessor.noPropertyPresentAction = NoPropertyPresentAction.ACCEPT_NEW_DEFAULT
    val dialog = registerDialogDisposable(
      AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(), r8FullModeDefaultProcessor, false))
    val comboBox = UIUtil.findComponentOfType(dialog.createCenterPanel(), ComboBox::class.java)!!
    comboBox.selectedItem = NoPropertyPresentAction.INSERT_OLD_DEFAULT
    val previewAction = dialog.createActions().first { it.getValue(Action.NAME) == "Show Usages" }
    previewAction.actionPerformed(null)
    assertTrue(dialog.isOK)
    assertEquals(NoPropertyPresentAction.INSERT_OLD_DEFAULT, r8FullModeDefaultProcessor.noPropertyPresentAction)
    assertTrue(isPreviewUsagesField.getBoolean(processor))
  }

  @Test
  fun testUpgradeVersionTextIfAgpClasspathEnabled() {
    val fromVersion = "4.1.0"
    val toVersion = "4.2.0-alpha05"
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse(fromVersion), AgpVersion.parse(toVersion))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.agpVersionRefactoringProcessor.isEnabled = true
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    val editorPanes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), JEditorPane::class.java)
    assertSize(1, editorPanes)
    assertTrue(editorPanes[0].text.contains("from\\s+Android\\s+Gradle\\s+Plugin\\s+version\\s+$fromVersion".toRegex()))
    assertTrue(editorPanes[0].text.contains("to\\s+version\\s+$toVersion".toRegex()))
  }

  @Test
  fun testNoUpgradeVersionTextIfAgpClasspathNotEnabled() {
    val fromVersion = "4.1.0"
    val toVersion = "4.2.0-alpha05"
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse(fromVersion), AgpVersion.parse(toVersion))
    processor.setJava8DefaultIsAlwaysNoOpForProject(false)
    processor.agpVersionRefactoringProcessor.isEnabled = false
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    val editorPanes = UIUtil.findComponentsOfType(dialog.createCenterPanel(), JEditorPane::class.java)
    assertSize(1, editorPanes)
    assertFalse(editorPanes[0].text.contains("version\\s+$fromVersion".toRegex()))
    assertFalse(editorPanes[0].text.contains("to\\s+version\\s+$toVersion".toRegex()))
  }

  @Test
  fun testDialogSetsBackFromPreviewAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    assertNull(processor.backFromPreviewAction)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false))
    assertNotNull(processor.backFromPreviewAction)
  }

  @Test
  fun testDialogKeepingProcessorStateSetsBackFromPreviewAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    assertNull(processor.backFromPreviewAction)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true))
    assertNotNull(processor.backFromPreviewAction)
  }

  @Test
  fun testRecreatedDialogPreservesBackFromPreviewAction() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    assertNull(processor.backFromPreviewAction)
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, processor.getJava8DefaultRefactoringProcessor(),
                                                                               processor.getR8FullModeDefaultRefactoringProcessor(), false, true,
                                                                               true))
    assertNull(processor.backFromPreviewAction)
  }

  @Test
  fun testTwoArgConstructor() {
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    val dialog = registerDialogDisposable(AgpUpgradeRefactoringProcessorDialog(processor, false))
    val java8Field = AgpUpgradeRefactoringProcessorDialog::class.java.getDeclaredField("myJava8Processor").apply { isAccessible = true }
    val r8Field = AgpUpgradeRefactoringProcessorDialog::class.java.getDeclaredField("myR8FullModeProcessor").apply { isAccessible = true }
    assertEquals(processor.getJava8DefaultRefactoringProcessor(), java8Field.get(dialog))
    assertEquals(processor.getR8FullModeDefaultRefactoringProcessor(), r8Field.get(dialog))
  }
}