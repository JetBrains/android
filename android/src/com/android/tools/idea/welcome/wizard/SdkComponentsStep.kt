/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.wizard.deprecated.SdkComponentsStepForm
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/** Wizard page for selecting SDK components to download. */
@UiThread
class SdkComponentsStep(
  model: FirstRunWizardModel,
  val project: Project?,
  val mode: FirstRunWizardMode,
  val licenseAgreementStep: LicenseAgreementStep?,
  private val tracker: FirstRunWizardTracker,
) : ModelWizardStep<FirstRunWizardModel>(model, "SDK Components Setup") {
  private val form = SdkComponentsStepForm()
  private val rootNode = model.componentTree
  private val controller =
    object : SdkComponentsStepController(project, mode, rootNode, model.localHandlerProperty) {
      @UiThread
      override fun setError(icon: Icon?, message: String?) {
        form.setErrorIcon(icon)
        form.setErrorMessage(message)
      }

      @UiThread
      override fun onLoadingStarted() {
        form.startLoading()
        validate()
        updateDiskSizes()
      }

      @UiThread
      override fun onLoadingFinished() {
        validate()
        updateDiskSizes()
        form.stopLoading()
      }

      @UiThread
      override fun onLoadingError() {
        validate()
        updateDiskSizes()
        form.setLoadingText("Error loading components")
      }

      @UiThread
      override fun reloadLicenseAgreementStep() {
        licenseAgreementStep?.reload()
      }
    }
  private val tableModel: SdkComponentsTableModel =
    SdkComponentsTableModel(model.componentTree).apply { form.setTableModel(this) }
  private val isValid = BoolValueProperty(false)

  init {
    Disposer.register(this, form)
    Disposer.register(this, controller)

    form.path.textField.document.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          validate()
          val updated =
            controller.onPathUpdated(
              form.path.getText(),
              ModalityState.stateForComponent(form.contents),
            )
          if (updated) tracker.trackSdkInstallLocationChanged()
        }
      }
    )

    form.setCellRenderer(
      object : SdkComponentsRenderer(tableModel, form.componentsTable) {
        override fun onCheckboxUpdated() {
          updateDiskSizes()
          licenseAgreementStep?.reload()
        }
      }
    )
    form.setCellEditor(
      object : SdkComponentsRenderer(tableModel, form.componentsTable) {
        override fun onCheckboxUpdated() {
          updateDiskSizes()
          licenseAgreementStep?.reload()
        }
      }
    )
  }

  override fun getComponent(): JComponent = form.contents

  override fun getPreferredFocusComponent(): JComponent = form.componentsTable

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    super.onWizardStarting(wizard)

    form.path.text = model.sdkInstallLocation?.toFile()?.absolutePath ?: ""
    updateDiskSizes()

    if (!rootNode.immediateChildren.isEmpty()) {
      form.componentsTable.selectionModel.setSelectionInterval(0, 0)
    }
  }

  override fun onEntering() {
    super.onEntering()

    form.path.text = model.sdkInstallLocation?.toFile()?.absolutePath ?: ""
    updateDiskSizes()
    validate()
  }

  override fun shouldShow(): Boolean {
    val installationType = model.installationType ?: FirstRunWizardModel.InstallationType.CUSTOM
    return controller.isStepVisible(
      installationType == FirstRunWizardModel.InstallationType.CUSTOM,
      model.sdkInstallLocation?.toFile()?.absolutePath ?: "",
    )
  }

  override fun canGoForward() = isValid

  override fun onProceeding() {
    super.onProceeding()
    controller.warnIfRequiredComponentsUnavailable()
  }

  override fun onShowing() {
    super.onShowing()
    tracker.trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.SDK_COMPONENTS)
  }

  private fun updateDiskSizes() {
    form.setDiskSpace(
      SdkComponentsStepUtils.getDiskSpace(model.sdkInstallLocation?.toFile()?.absolutePath)
    )
    form.setDownloadSize(controller.componentsSize)
  }

  private fun validate() {
    isValid.set(controller.validate(form.path.text))
  }
}
