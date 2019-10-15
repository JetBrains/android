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

import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.gradle.structure.IdeSdksConfigurable
import com.android.tools.idea.gradle.structure.IdeSdksConfigurable.getLocationFromComboBoxWithBrowseButton
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ComboboxWithBrowseButton
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import java.awt.event.ItemEvent
import java.io.File

// TODO(qumeric): dispose listeners etc? check ConfigureTemplateParametersStep etc.
/**
 * Wizard step for JDK setup.
 */
class JdkSetupStep : ModelWizardStep.WithoutModel("Select default JDK location") {
  private val jdkDirectory = JBLabel("Select the directory where the Java Development Kit (JDK) is located.")
  private val jdkLocationComboBox = ComboboxWithBrowseButton()
  private val jdkWarningLink = HyperlinkLabel()
  private val jdkWarningLabel = JBLabel("Label")

  private val jdkPanel = VerticalPanel(7, 1) {
    // TODO(qumeric) vspacer
    elem(jdkDirectory, 8, 0, 0, 0)
    // TODO(qumeric) vspacer
    elem(jdkLocationComboBox, 0, 1, 7, 3)
    // TODO(qumeric) vspacer
    elem(jdkWarningLabel, 8, 0, 0, 0)
    elem(jdkWarningLink,  8, 2, 0, 0)
  }.build()

  private val validatorPanel = ValidatorPanel(this, wrappedWithVScroll(jdkPanel))
  private val invalidPathMessage = StringValueProperty()
  private val isValidJdkPath = BoolValueProperty(false)
  private val jdkLocation: File get() = getLocationFromComboBoxWithBrowseButton(jdkLocationComboBox)

  init {
    val descriptor = createSingleFolderDescriptor { file ->
      validateJdkPath(file) ?: throw IllegalArgumentException(IdeSdksConfigurable.generateChooseValidJdkDirectoryError())
      setJdkLocationComboBox(file)
    }

    jdkLocationComboBox.addBrowseFolderListener(null, descriptor)

    val comboBox = jdkLocationComboBox.comboBox
    comboBox.addActionListener {
      validateJdkPath(jdkLocation)
    }

    fun addJdkIfValid(path: File?, label: String) {
      path ?: return
      val validatedPath = validateJdkPath(path) ?: return
      comboBox.addItem(IdeSdksConfigurable.LabelAndFileForLocation(label, validatedPath))
    }

    val embeddedPath = IdeSdks.getInstance().embeddedJdkPath
    addJdkIfValid(embeddedPath, "Embedded JDK")

    val javaHomePath = IdeSdks.getJdkFromJavaHome()
    if (javaHomePath != null) {
      addJdkIfValid(File(javaHomePath), "JAVA_HOME")
    }

    comboBox.isEditable = true
    comboBox.addItemListener { event ->
      val selectedItem = event.item
      if (event.stateChange == ItemEvent.SELECTED && selectedItem is IdeSdksConfigurable.LabelAndFileForLocation) {
        invokeLater { setJdkLocationComboBox(selectedItem.file) }
      }
    }
    setJdkLocationComboBox(embeddedPath)
  }

  override fun onEntering() {
    validatorPanel.registerMessageSource(invalidPathMessage)
  }

  override fun canGoForward(): ObservableBool = isValidJdkPath

  override fun onProceeding() {
    val path = FilePaths.toSystemDependentPath(jdkLocation.path)
    ApplicationManager.getApplication().runWriteAction { IdeSdks.getInstance().setJdkPath(path!!) }
    //myState.put(WizardConstants.KEY_JDK_LOCATION, path!!.path)
  }

  override fun getPreferredFocusComponent() = jdkPanel

  override fun getComponent() = validatorPanel

  private fun validateJdkPath(file: File): File? {
    val possiblePath = IdeSdks.getInstance().validateJdkPath(file)
    if (possiblePath != null) {
      setJdkLocationComboBox(possiblePath)
      isValidJdkPath.set(true)
      return possiblePath
    }
    isValidJdkPath.set(false)
    return null

    // TODO(qumeric): replace it with PathValidator, like:
    val validator = PathValidator.Builder().withCommonRules().build("Android SDK location")
    val validationResult = validator.validate(jdkLocation)
    invalidPathMessage.set(validationResult.message)
    val isError = validationResult.severity != Validator.Severity.ERROR
    isValidJdkPath.set(isError)
    return file.takeIf { !isError }
  }

  private fun setJdkLocationComboBox(path: File?) {
    jdkLocationComboBox.comboBox.selectedItem = path?.toSystemDependentName()
    val visible = !IdeSdks.isSameAsJavaHomeJdk(getLocationFromComboBoxWithBrowseButton(jdkLocationComboBox))
    jdkWarningLink.isVisible = visible
    jdkWarningLabel.isVisible = visible
  }

  private fun File.toSystemDependentName() = FileUtilRt.toSystemDependentName(path)
}

// TODO(qumeric) make private
fun createSingleFolderDescriptor(validation: (File) -> Unit) =
  object : FileChooserDescriptor(false, true, false, false, false, false) {
    override fun validateSelectedFiles(files: Array<VirtualFile>) {
      files.map(VirtualFile::toIoFile).forEach(validation)
    }
  }.apply<FileChooserDescriptor> {
    withShowHiddenFiles(SystemInfo.isMac)
    title = "Choose JDK Location"
  }
