/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.backup.BackupType.CLOUD
import com.android.backup.testing.BackupFileHelper
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import kotlin.io.path.pathString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [RestoreRunConfigSection] */
@RunWith(JUnit4::class)
class RestoreRunConfigSectionTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val temporaryFolder =
    TemporaryFolder(TemporaryDirectory.generateTemporaryPath("").parent.toFile())

  @get:Rule val rule = RuleChain(projectRule, temporaryFolder)

  private val backupFileHelper = BackupFileHelper(temporaryFolder)

  @Test
  fun validate_absolute() {
    val section = RestoreRunConfigSection(project)
    val configuration = AndroidRunConfiguration(project, AndroidRunConfigurationType().factory)
    val backupFile = backupFileHelper.createBackupFile("com.app", "11223344556677889900", CLOUD)
    configuration.RESTORE_ENABLED = true
    configuration.RESTORE_FILE = backupFile.pathString

    val errors = section.validate(configuration)

    assertThat(errors.map { it.message }).isEmpty()
  }

  @Test
  fun validate_relative() {
    val section = RestoreRunConfigSection(project)
    val configuration = AndroidRunConfiguration(project, AndroidRunConfigurationType().factory)
    val backupFile = backupFileHelper.createBackupFile("com.app", "11223344556677889900", CLOUD)
    configuration.RESTORE_ENABLED = true
    configuration.RESTORE_FILE = backupFile.relativeToProject(project).pathString

    val errors = section.validate(configuration)

    assertThat(errors.map { it.message }).isEmpty()
  }
}
