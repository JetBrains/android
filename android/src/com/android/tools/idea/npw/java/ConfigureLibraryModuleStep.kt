/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.java

import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getInitialDomain
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.DomainToPackageExpression
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.validator.ClassNameValidator
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.npw.validator.PackageNameValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.Label
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.PropertyBinding
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JTextField

class ConfigureLibraryModuleStep(
  model: NewLibraryModuleModel, title: String
) : SkippableWizardStep<NewLibraryModuleModel>(model, title) {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()
  private val moduleValidator = ModuleValidator(model.project)

  private lateinit var libraryName: JTextField
  private lateinit var packageName: LabelWithEditButton
  private lateinit var className: JTextField
  private lateinit var language: ComboBox<Language>

  private val panel = panel {
    val initialLibraryNameValue = WizardUtils.getUniqueName(model.moduleName.get(), moduleValidator)
    row {
      label(AndroidBundle.message("android.wizard.module.config.title"), style = UIUtil.ComponentStyle.LARGE, bold = true)
    }
    row {
      val label = Label("Library name:")
      cell {
        label()
        ContextHelpLabel.create(AndroidBundle.message("android.wizard.module.help.name"))()
      }
      libraryName = textField({ initialLibraryNameValue }, { model.moduleName.set(it) }).component
      label.labelFor = libraryName
    }
    row {
      val label = Label("Package name:")
      label()
      packageName = labelWithEditButton(
        { DomainToPackageExpression(StringValueProperty(getInitialDomain()), StringValueProperty(initialLibraryNameValue)).get() },
        { model.packageName.set(it) }
      ).component
      label.labelFor = packageName.textField
    }
    row {
      val label = Label("Class name:")
      label()
      className = textField({ model.className.get() }, { model.className.set(it) }).component
      label.labelFor = className
    }
    row {
      val label = Label("Language:")
      right {
        language = comboBox(LanguageComboProvider().createComponent().model, { Language.KOTLIN }, { model.language.value = it!! }).component
      }
      label.labelFor = language
    }
  }

  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, panel)
  private val rootPanel = StudioWizardStepPanel(validatorPanel)

  init {
    validatorPanel.apply {
      registerValidator(TextProperty(libraryName), moduleValidator)
      registerValidator(TextProperty(packageName), PackageNameValidator())
      registerValidator(TextProperty(className), ClassNameValidator())
    }

    val computedPackageName = DomainToPackageExpression(StringValueProperty(getInitialDomain()), TextProperty(libraryName))
    val isPackageNameSynced = BoolValueProperty(true)
    val packageNameText = TextProperty(packageName)
    bindings.bind(packageNameText, computedPackageName, isPackageNameSynced)
    listeners.listen(packageNameText) { value -> isPackageNameSynced.set(value == computedPackageName.get()) }
  }

  override fun onProceeding() {
    panel.apply()
    // Now that the module name was validated, update the model template
    with(model) {
      template.set(createDefaultTemplateAt(project.basePath!!, moduleName.get()))
    }
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> = listOf()

  override fun canGoForward() = validatorPanel.hasErrors().not()

  override fun getComponent() = rootPanel

  override fun getPreferredFocusComponent() = libraryName

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}

fun Cell.labelWithEditButton(getter: () -> String, setter: (String) -> Unit): CellBuilder<LabelWithEditButton> {
  val component = LabelWithEditButton(getter())
  val builder = component(growX, pushX)
  return builder.withBinding(LabelWithEditButton::getText, LabelWithEditButton::setText, PropertyBinding(getter, setter))
}

