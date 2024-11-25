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

import com.android.io.CancellableFileIo
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.wizard.deprecated.SdkComponentsRenderer
import com.android.tools.idea.welcome.wizard.deprecated.SdkComponentsStepController
import com.android.tools.idea.welcome.wizard.deprecated.SdkComponentsStepForm
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.sdk.AndroidSdkData
import com.android.tools.sdk.isValid
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.util.containers.isEmpty
import com.intellij.util.containers.notNullize
import org.jetbrains.annotations.Contract
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Wizard page for selecting SDK components to download.
 */
class SdkComponentsStep(
  model: FirstRunModel,
  val project: Project?,
  val mode: FirstRunWizardMode,
  val licenseAgreementStep: LicenseAgreementStep?,
  parent: Disposable
) : ModelWizardStep<FirstRunModel>(model, "SDK Components Setup") {
  private val form = SdkComponentsStepForm()
  private val rootNode = model.componentTree
  private val controller = object : SdkComponentsStepController(project, mode, rootNode, model.localHandlerProperty) {
    override fun setError(icon: Icon?, message: String?) {
      form.setErrorIcon(icon)
      form.setErrorMessage(message)
    }

    override fun onLoadingStarted() {
      form.startLoading()
      validate()
      updateDiskSizes()
    }

    override fun onLoadingFinished() {
      validate()
      updateDiskSizes()
      form.stopLoading()
    }

    override fun onLoadingError() {
      validate()
      updateDiskSizes()
      form.setLoadingText("Error loading components")
    }

    override fun reloadLicenseAgreementStep() {
      licenseAgreementStep?.reload()
    }
  }
  private val tableModel: ComponentsTableModel = ComponentsTableModel(model.componentTree).apply {
    form.setTableModel(this)
  }
  private val isValid = BoolValueProperty(false)

  init {
    Disposer.register(parent, form)

    form.path.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        validate()
        controller.onPathUpdated(form.path.getText())
      }
    })

    val renderer = object : SdkComponentsRenderer(tableModel, form.componentsTable) {
      override fun onCheckboxUpdated() {
        updateDiskSizes()
      }
    }
    form.setRenderer(renderer)
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
    updateDiskSizes();
    validate()
  }

  override fun shouldShow(): Boolean {
    return controller.isStepVisible(model.customInstall, model.sdkInstallLocation?.toFile()?.absolutePath ?: "")
  }

  override fun canGoForward() = isValid

  override fun onProceeding() {
    controller.warnIfRequiredComponentsUnavailable()
  }

  private fun updateDiskSizes() {
    form.setDiskSpace(getDiskSpace(model.sdkInstallLocation?.toFile()?.absolutePath))
    form.setDownloadSize(controller.componentsSize)
  }

  private fun validate() {
    isValid.set(controller.validate(form.path.text))
  }
}

// TODO(qumeric): make private
@Contract("null->null")
fun getExistingParentFile(path: String?): File? {
  if (path.isNullOrEmpty()) {
    return null
  }

  return generateSequence(File(path).absoluteFile) { it.parentFile }.firstOrNull(File::exists)
}

// TODO(qumeric): make private
fun getDiskSpace(path: String?): String {
  val file = getTargetFilesystem(path) ?: return ""
  val available = getSizeLabel(file.freeSpace)
  return if (SystemInfo.isWindows) {
    val driveName = generateSequence(file, File::getParentFile).last().name
    "$available (drive $driveName)"
  }
  else {
    available
  }
}

// TODO(qumeric): make private
fun getTargetFilesystem(path: String?): File? = getExistingParentFile(path) ?: File.listRoots().firstOrNull()

@Contract("null->false")
// TODO(qumeric): make private
fun isExistingSdk(path: String?): Boolean {
  if (path.isNullOrBlank()) {
    return false
  }
  return File(path).run { isDirectory && isValid(this) }
}

@Contract("null->false")
// TODO(qumeric): make private
fun isNonEmptyNonSdk(path: String?): Boolean {
  if (path == null) {
    return false
  }
  val file = File(path)
  return file.exists() && !CancellableFileIo.list(file.toPath()).notNullize().isEmpty() && AndroidSdkData.getSdkData(file) == null
}