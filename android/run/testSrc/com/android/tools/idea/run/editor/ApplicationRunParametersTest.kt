/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.editor

import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.backup.testing.FakeBackupManager
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.ui.components.JBCheckBox
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.event.ActionEvent

class ApplicationRunParametersTest {
  private lateinit var myApplicationRunParameters: ApplicationRunParameters<AndroidRunConfiguration>
  private lateinit var myModuleSelector: ConfigurationModuleSelector

  private val projectRule: AndroidProjectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.BASIC)
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule)

  @Before
  fun setUp() {
    projectRule.project.registerOrReplaceServiceInstance(BackupManager::class.java, FakeBackupManager(), disposableRule.disposable)
    myModuleSelector = mock<ConfigurationModuleSelector>()
    myApplicationRunParameters = ApplicationRunParameters(projectRule.project, myModuleSelector)
  }

  @Test
  fun testToggleInstantAppWithNullModule() {
    whenever(myModuleSelector.module).thenReturn(null)
    val myInstantAppDeployCheckbox = ApplicationRunParameters::class.java.getDeclaredField("myInstantAppDeployCheckBox")
    myInstantAppDeployCheckbox.isAccessible = true
    val event = mock<ActionEvent>()
    whenever(event.source).thenReturn(myInstantAppDeployCheckbox.get(myApplicationRunParameters))
    myApplicationRunParameters.actionPerformed(event)
  }

  @Test
  fun testInstantAppCheckboxDisabledWithNullModule() {
    whenever(myModuleSelector.module).thenReturn(null)
    myApplicationRunParameters.onModuleChanged()
    val myInstantAppDeployCheckboxField = ApplicationRunParameters::class.java.getDeclaredField("myInstantAppDeployCheckBox")
    myInstantAppDeployCheckboxField.isAccessible = true
    val myInstantAppDeployCheckbox = myInstantAppDeployCheckboxField.get(myApplicationRunParameters) as JBCheckBox
    assertFalse(myInstantAppDeployCheckbox.isEnabled)
  }
}
