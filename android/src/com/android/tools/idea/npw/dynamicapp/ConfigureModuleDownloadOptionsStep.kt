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
package com.android.tools.idea.npw.dynamicapp

import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.validator.ProjectNameValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.expressions.bool.IsEqualToExpression
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.ui.wizard.WizardUtils.wrapWithVScroll
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import java.util.Optional
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextField

class ConfigureModuleDownloadOptionsStep(
  model: DynamicFeatureModel
) : ModelWizardStep<DynamicFeatureModel?>(model, AndroidBundle.message("android.wizard.module.new.dynamic.download.options")) {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()

  private val headerInfo: JEditorPane = SwingHelper.createHtmlViewer(false, null, null, null).apply {
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    SwingHelper.setHtml(
      this, "Dynamic feature modules can be delivered on-demand, included at install time," +
            " or included conditionally based on device features or user country." +
            " <a href='https://developer.android.com/studio/projects/dynamic-delivery/overview'>Learn more</a>",
      UIUtil.getLabelForeground())
  }
  private val featureTitle: JTextField = JBTextField()
  private val installationOptionCombo: JComboBox<DownloadInstallKind> = ComboBox(DefaultComboBoxModel(DownloadInstallKind.values()))
  private val downloadConditionsForm: ModuleDownloadConditions = ModuleDownloadConditions().apply {
    setModel(model.deviceFeatures)
  }
  private val fusingCheckbox: JCheckBox = JBCheckBox("Fusing (include module at install-time for pre-Lollipop devices)")

  val panel: DialogPanel = panel {
    row {
      headerInfo()
    }

    row {
      labelFor(
        "Module title (this may be visible to users)",
        featureTitle,
        "The platform uses this title to identify the module to users when," +
        " for example, confirming whether the user wants to download the module."
      )
    }

    row {
      featureTitle()
    }

    row {
      labelFor(
        "Install-time inclusion",
        installationOptionCombo,
        "Specify whether to include this module at install-time unconditionally, or based on device features."
      )
    }

    row {
      installationOptionCombo()
    }

    row {
      downloadConditionsForm.myRootPanel()
    }

    row {
      cell {
        fusingCheckbox()
        ContextHelpLabel.createWithLink(
          null, "Enable Fusing if you want this module to be available to devices running Android 4.4 (API level 20) and lower.", "Learn more"
        ) { BrowserUtil.browse(linkUrl) }()
      }
    }

    row {
      label("Pre-Lollipop devices do not support on-demand modules", style = UIUtil.ComponentStyle.SMALL,
            fontColor = UIUtil.FontColor.BRIGHTER)
    }
  }

  private val validatorPanel = ValidatorPanel(this, wrapWithVScroll(panel))

  init {
    FormScalingUtil.scaleComponentTree(this.javaClass, validatorPanel)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    bindings.apply {
      bindTwoWay(TextProperty(featureTitle), model.featureTitle)
      bindTwoWay(SelectedProperty(fusingCheckbox), model.featureFusing)
      bindTwoWay(SelectedItemProperty(installationOptionCombo), model.downloadInstallKind)
    }

    // Initialize "conditions" sub-form
    val isConditionalPanelActive = IsEqualToExpression(
      model.downloadInstallKind,
      Optional.of(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS)
    )
    downloadConditionsForm.init(model.project, validatorPanel, isConditionalPanelActive)

    // Show the "conditions" panel only if the dropdown selection is "with conditions"
    listeners.listenAndFire(model.downloadInstallKind) { value: Optional<DownloadInstallKind> ->
      downloadConditionsForm.myRootPanel.isVisible =
        value.isPresent && value.get() === DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS
    }
    validatorPanel.registerValidator(model.featureTitle, ProjectNameValidator())
  }


  override fun onEntering() {
    featureTitle.selectAll()
  }

  override fun shouldShow(): Boolean = !model.isInstant

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun getComponent(): JComponent = validatorPanel

  override fun getPreferredFocusComponent(): JComponent? = featureTitle

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}

private const val linkUrl = AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/dynamic-delivery/fusing"
