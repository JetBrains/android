/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.InstallableSdkComponentTreeNode
import com.android.tools.idea.welcome.install.SdkComponentCategoryTreeNode
import com.android.tools.idea.welcome.install.SdkComponentTreeNode
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.table.JBTable
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JLabel
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
class SdkComponentsStepTest {

  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain =
    RuleChain(
      projectRule,
      HeadlessDialogRule(),
      EdtRule(),
    ) // AndroidProjectRule must get initialized off the EDT thread

  private lateinit var mode: FirstRunWizardMode
  private lateinit var sdkPath: Path
  private lateinit var model: FirstRunWizardModel
  private lateinit var licenseAgreementStep: LicenseAgreementStep

  @Before
  fun setup() {
    licenseAgreementStep = mock(LicenseAgreementStep::class.java)
    mode = FirstRunWizardMode.NEW_INSTALL
    sdkPath = FileUtil.createTempDirectory("sdk", null).toPath()
    model = spy(FirstRunWizardModel(mode, sdkPath, true, SdkComponentInstallerProvider()))

    val root =
      SdkComponentCategoryTreeNode(
        "Root",
        "Root node that is not supposed to appear in the UI",
        listOf(
          FakeOptionalInstallableSdkComponent("Optional component"),
          FakeOptionalInstallableSdkComponent("Another optional component"),
        ),
      )
    whenever(model.componentTree).thenReturn(root)
  }

  @Test
  fun titleIsCorrect() {
    val sdkComponentsStep = SdkComponentsStep(model, null, mode, licenseAgreementStep)
    runInWizardDialog(sdkComponentsStep) { fakeUi ->
      val title =
        checkNotNull(fakeUi.findComponent<JLabel> { it.text.contains("SDK Components Setup") })
      assertTrue { fakeUi.isShowing(title) }
    }
  }

  @Test
  fun licensesReloadedWhenComponentSelectionChanges() {
    val sdkComponentsStep = SdkComponentsStep(model, null, mode, licenseAgreementStep)
    runInWizardDialog(sdkComponentsStep) { fakeUi ->
      val table = checkNotNull(fakeUi.findComponent<JBTable>())
      assertThat(table.model.rowCount).isEqualTo(2)

      verify(licenseAgreementStep, times(0)).reload()
      getSdkComponentCheckbox("Optional component", table).doClick()
      verify(licenseAgreementStep, times(1)).reload()
    }
  }

  private fun runInWizardDialog(
    sdkComponentsStep: SdkComponentsStep,
    action: (fakeUi: FakeUi) -> Unit,
  ) {
    val modelWizardDialog = wrapInWizardDialog(sdkComponentsStep)
    createModalDialogAndInteractWithIt({ modelWizardDialog.show() }) {
      val fakeUi = FakeUi(modelWizardDialog.contentPane, createFakeWindow = true)
      action(fakeUi)
    }
  }

  private fun wrapInWizardDialog(sdkComponentsStep: SdkComponentsStep): ModelWizardDialog {
    val modelWizard = ModelWizard.Builder(sdkComponentsStep).build()
    return StudioWizardDialogBuilder(modelWizard, "SDK Setup").build()
  }

  private fun getSdkComponentCheckbox(containingText: String, inTable: JBTable): JCheckBox {
    val renderedCell =
      inTable
        .getCellEditor(0, 0)
        .getTableCellEditorComponent(inTable, inTable.getValueAt(0, 0), false, 0, 0)
    return checkNotNull(renderedCell.findDescendant<JCheckBox> { it.text == containingText })
  }

  class FakeOptionalInstallableSdkComponent(name: String) : SdkComponentTreeNode(name) {
    private var isSelected = false
    override val label: String = name
    override val childrenToInstall: Collection<InstallableSdkComponentTreeNode> = emptyList()
    override val isChecked: Boolean = isSelected
    override val immediateChildren: Collection<SdkComponentTreeNode> = emptySet()
    override val isEnabled: Boolean = true

    override fun updateState(handler: AndroidSdkHandler) {}

    override fun toggle(isSelected: Boolean) {
      this.isSelected = isSelected
    }
  }
}
