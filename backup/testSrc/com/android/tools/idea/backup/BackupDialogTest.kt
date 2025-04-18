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

import com.android.backup.BackupType
import com.android.backup.BackupType.CLOUD
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.testutils.AssumeUtil.assumeNotWindows
import com.android.testutils.AssumeUtil.assumeWindows
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.backup.testing.clickOk
import com.android.tools.idea.backup.testing.findComponent
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.TextAccessor
import javax.swing.JEditorPane
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val WARNING_DTD_HTML =
  """
    <html>
      <head>
        
      </head>
      <body>
        App-data won't be backed up as allowBackup property is false.<br>Backup 
        may contain Restore Keys, if present for the app.<br>(<a href="http://bar.com/">Learn 
        more</a>)
      </body>
    </html>
"""
    .trimIndent()

private val WARNING_CLOUD_HTML =
  """
    <html>
      <head>
        
      </head>
      <body>
        App-data won't be backed up as allowBackup property is false.<br>Restore 
        Keys backup is not supported via this tool for Cloud.<br>backup type. (<a href="http://bar.com/">Learn 
        more</a>)
      </body>
    </html>
"""
    .trimIndent()

/** Tests for [BackupDialog] */
@RunWith(JUnit4::class)
@RunsInEdt
class BackupDialogTest {
  private val projectRule = ProjectRule()
  private val temporaryFolder = TemporaryFolder()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      temporaryFolder,
      HeadlessDialogRule(),
      disposableRule,
      EdtRule(),
    )

  private val project
    get() = projectRule.project

  private val projectDir
    get() = project.guessProjectDir()!!.toNioPath()

  @After
  fun tearDown() {
    // There is no shared fake/mock of PropertiesComponent so the easiest thing to do is to reset it
    // after each run
    PropertiesComponent.getInstance().unsetValue(BackupDialog.LAST_USED_TYPE_KEY)
    PropertiesComponent.getInstance().unsetValue(BackupDialog.LAST_USED_FILE_KEY)
  }

  @Test
  fun showDialog_defaultValues() {
    createDialog {
      it.clickOk()
      assertThat(it.type).isEqualTo(DEVICE_TO_DEVICE)
      assertThat(it.backupPath).isEqualTo(projectDir.resolve("application.backup"))
    }
  }

  @Test
  fun showDialog_appComboBox_order() {
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app2", "app3")
        }
      },
      disposableRule.disposable,
    )

    createDialog(
      initialApplication = "app2",
      debuggableApps = listOf("app4", "app3", "app2", "app1"),
    ) {
      val applicationComboBox = it.findComponent<ComboBox<String>>("applicationIdComboBox")
      assertThat(applicationComboBox.getItemAt(0)).isEqualTo("app1")
      assertThat(applicationComboBox.getItemAt(1)).isEqualTo("app2")
      assertThat(applicationComboBox.getItemAt(2)).isEqualTo("app3")
      assertThat(applicationComboBox.getItemAt(3)).isEqualTo("app4")
    }
  }

  @Test
  fun showDialog_appComboBox_projectApp() {
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app2", "app3")
        }
      },
      disposableRule.disposable,
    )

    createDialog(
      initialApplication = "app2",
      debuggableApps = listOf("app4", "app3", "app2", "app1"),
    ) {
      val applicationComboBox = it.findComponent<ComboBox<String>>("applicationIdComboBox")
      assertThat(applicationComboBox.item).isEqualTo("app2")
    }
  }

  @Test
  fun showDialog_appComboBox_debuggableApp() {
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app2", "app3")
        }
      },
      disposableRule.disposable,
    )

    createDialog(
      initialApplication = "app4",
      debuggableApps = listOf("app4", "app3", "app2", "app1"),
    ) {
      val applicationComboBox = it.findComponent<ComboBox<String>>("applicationIdComboBox")
      assertThat(applicationComboBox.item).isEqualTo("app4")
    }
  }

  @Test
  fun showDialog_appComboBox_nonDebuggableApp() {
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app2", "app3")
        }
      },
      disposableRule.disposable,
    )

    createDialog(
      initialApplication = "app5",
      debuggableApps = listOf("app4", "app3", "app2", "app1"),
    ) {
      val applicationComboBox = it.findComponent<ComboBox<String>>("applicationIdComboBox")
      assertThat(applicationComboBox.item).isEqualTo("app2")
    }
  }

  @Test
  fun showDialog_changeType() {
    createDialog {
      it.findComponent<ComboBox<BackupType>>("typeComboBox").item = CLOUD

      it.clickOk()

      assertThat(it.type).isEqualTo(CLOUD)
    }
  }

  @Test
  fun showDialog_updatesLastUsedType() {
    createDialog {
      it.findComponent<ComboBox<BackupType>>("typeComboBox").item = CLOUD
      it.clickOk()
    }
    createDialog {
      it.clickOk()

      assertThat(it.type).isEqualTo(CLOUD)
    }
  }

  @Test
  fun showDialog_changeFileDirectly() {
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "someDir/foo.backup"

      it.clickOk()

      assertThat(it.backupPath).isEqualTo(projectDir.resolve("someDir/foo.backup"))
    }
  }

  @Test
  fun showDialog_changeFile_withoutExtension() {
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "someDir/foo"

      it.clickOk()

      assertThat(it.backupPath).isEqualTo(projectDir.resolve("someDir/foo.backup"))
    }
  }

  @Test
  fun showDialog_changeFile_absolutePath() {
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "/someDir/foo"

      it.clickOk()

      assertThat(it.backupPath).isEqualTo(projectDir.resolve("/someDir/foo.backup"))
    }
  }

  @Test
  fun showDialog_updatesLastUsedFile() {
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "someDir/foo"
      it.clickOk()
    }
    createDialog {
      it.clickOk()

      assertThat(it.backupPath).isEqualTo(projectDir.resolve("someDir/foo.backup"))
    }
  }

  @Test
  fun showDialog_okEnabled() {
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "someDir/foo"
      assertThat(it.isOKActionEnabled).isTrue()
    }
  }

  @Test
  fun showDialog_emptyFile_okDisabled() {
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = ""
      assertThat(it.isOKActionEnabled).isFalse()
    }
  }

  @Test
  fun showDialog_illegalPathColon_okDisabled() {
    assumeWindows()
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "foo:bar"
      assertThat(it.isOKActionEnabled).isFalse()
    }
  }

  @Test
  fun showDialog_illegalPathBackslash_okDisabled() {
    assumeNotWindows()
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = "foo\\bar"
      assertThat(it.isOKActionEnabled).isFalse()
    }
  }

  @Test
  fun showDialog_illegalPathDirectory_okDisabled() {
    val dir = temporaryFolder.newFolder("tmp").path
    createDialog {
      it.findComponent<TextAccessor>("fileTextField").text = dir
      assertThat(it.isOKActionEnabled).isFalse()
    }
  }

  @Test
  fun showDialog_backupEnabled() {
    createDialog(isBackupEnabled = true) {
      assertThat(it.findComponent<JEditorPane>("backupNotEnabledWarning").isVisible).isFalse()
      assertThat(it.isOKActionEnabled).isTrue()
    }
  }

  @Test
  fun showDialog_backupDisabled() {
    createDialog(isBackupEnabled = false) {
      val warning = it.findComponent<JEditorPane>("backupNotEnabledWarning")
      val typeCombo = it.findComponent<ComboBox<BackupType>>("typeComboBox")
      assertThat(warning.isVisible).isTrue()

      typeCombo.item = DEVICE_TO_DEVICE
      assertThat(warning.text.trim()).isEqualTo(WARNING_DTD_HTML)
      assertThat(it.isOKActionEnabled).isTrue()

      typeCombo.item = CLOUD
      assertThat(warning.text.trim()).isEqualTo(WARNING_CLOUD_HTML)
      assertThat(it.isOKActionEnabled).isFalse()

      typeCombo.item = CLOUD
      assertThat(warning.text.trim()).isEqualTo(WARNING_CLOUD_HTML)
      assertThat(it.isOKActionEnabled).isFalse()
    }
  }

  private fun createDialog(
    initialApplication: String = "app",
    debuggableApps: List<String> = emptyList(),
    isBackupEnabled: Boolean = true,
    dialogInteractor: (BackupDialog) -> Unit,
  ) {
    createModalDialogAndInteractWithIt(
      BackupDialog(project, initialApplication, debuggableApps, isBackupEnabled)::show
    ) {
      dialogInteractor(it as BackupDialog)
    }
  }
}
