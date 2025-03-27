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
package com.android.tools.idea.run

import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.backup.testing.FakeBackupManager
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidRunConfigurationForDynamicAppTest {

  private val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.DYNAMIC_APP)
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule)

  @Before
  fun setUp() {
    projectRule.project.registerOrReplaceServiceInstance(BackupManager::class.java, FakeBackupManager(), disposableRule.disposable)
  }

  @Test
  @Throws(Exception::class)
  fun testDynamicAppApks() {
    val configSettings = RunManager.getInstance(projectRule.project)
      .allSettings
      .find { it.configuration is AndroidRunConfiguration }!!

    val config = configSettings.configuration as AndroidRunConfiguration
    config.setModule(projectRule.module)
    try {
      configSettings.checkSettings()
    }
    catch (e: RuntimeConfigurationException) {
      Assert.fail(e.message)
    }
  }
}
