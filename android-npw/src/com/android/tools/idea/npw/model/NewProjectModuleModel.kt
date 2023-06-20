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
package com.android.tools.idea.npw.model

import com.android.SdkConstants
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createSampleTemplate
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_PROJECT
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.Locale

/**
 * Orchestrates creation of the new project. Creates three steps (Project, Model, Activity) and renders them in a proper order.
 */
class NewProjectModuleModel(private val projectModel: NewProjectModel) : WizardModel() {

  private val newModuleModel = NewAndroidModuleModel(
    projectModel,
    createSampleTemplate(),
    ":",
    formFactor = ObjectValueProperty(FormFactor.Mobile),
    category = ObjectValueProperty(Category.Activity),
    wizardContext = NEW_PROJECT
  ).apply {
    multiTemplateRenderer.incrementRenders()
  }

  @JvmField
  val formFactor = newModuleModel.formFactor

  /**
   * A model which is used at the optional step after usual activity configuring. Currently only used for Android Things.
   */
  @JvmField
  val extraRenderTemplateModel = RenderTemplateModel.fromModuleModel(newModuleModel, message("android.wizard.config.activity.title"))

  @JvmField
  val newRenderTemplate = OptionalValueProperty<Template>()

  @JvmField
  val hasCompanionApp = BoolValueProperty()

  fun androidSdkInfo(): OptionalProperty<AndroidVersionsInfo.VersionItem> = newModuleModel.androidSdkInfo

  override fun handleFinished() {
    initMainModule()
    val packageName = projectModel.packageName.get()
    val newRenderTemplateModel = createMainRenderModel().apply {
      addRenderDefaultTemplateValues(this, packageName)
    }
    if (hasCompanionApp.get() && newRenderTemplateModel.hasActivity) {
      val companionModuleModel = createCompanionModuleModel(projectModel)
      val companionRenderModel = createCompanionRenderModel(companionModuleModel, packageName)

      companionModuleModel.androidSdkInfo.value = androidSdkInfo().value

      companionModuleModel.handleFinished()
      companionRenderModel.handleFinished()
    }

    newModuleModel.handleFinished()

    if (newRenderTemplateModel == extraRenderTemplateModel) {
      return // Extra render is driven by the Wizard itself
    }

    if (newRenderTemplateModel.hasActivity) {
      newRenderTemplateModel.handleFinished()
    }
    else {
      newRenderTemplateModel.handleSkipped() // "No Activity" selected
    }
  }

  private fun initMainModule() {
    val moduleName: String = if (hasCompanionApp.get())
      getModuleName(formFactor.get())
    else
      SdkConstants.APP_PREFIX

    val moduleRoot = File(projectModel.projectLocation.get(), moduleName)

    newModuleModel.moduleName.set(moduleName)
    newModuleModel.template.set(createDefaultTemplateAt(moduleRoot))
  }

  private fun createMainRenderModel(): RenderTemplateModel = when {
    !extraRenderTemplateModel.hasActivity -> {
      RenderTemplateModel.fromModuleModel(newModuleModel).apply {
        if (newRenderTemplate.isPresent.get()) {
          newTemplate = newRenderTemplate.value
        }
      }
    }
    else -> extraRenderTemplateModel // Extra Render is visible. Use it.
  }
}

/**
 * The name of the template to use to construct a companion module.
 *
 * TODO: Consider updating this to the Compose/Material3 "Empty Activity" template
 * if the project is Kotlin-based
 */
internal const val COMPANION_MODULE_TEMPLATE_NAME = "Empty Views Activity"

private fun createCompanionModuleModel(projectModel: NewProjectModel): NewAndroidModuleModel {
  // Note: The companion Module is always a Mobile app
  val moduleName = getModuleName(FormFactor.Mobile)
  val moduleRoot = File(projectModel.projectLocation.get(), moduleName)
  val namedModuleTemplate = createDefaultTemplateAt(moduleRoot)
  val companionModuleModel = NewAndroidModuleModel(
    projectModel,
    namedModuleTemplate,
    ":",
    formFactor = ObjectValueProperty(FormFactor.Mobile),
    category = ObjectValueProperty(Category.Activity),
    wizardContext = NEW_PROJECT
  )
  companionModuleModel.multiTemplateRenderer.incrementRenders()
  companionModuleModel.moduleName.set(moduleName)

  return companionModuleModel
}

private fun createCompanionRenderModel(moduleModel: NewAndroidModuleModel, packageName: String): RenderTemplateModel {
  val companionRenderModel = RenderTemplateModel.fromModuleModel(moduleModel).apply {
    newTemplate = TemplateResolver.getAllTemplates().first { it.name == COMPANION_MODULE_TEMPLATE_NAME }
  }
  addRenderDefaultTemplateValues(companionRenderModel, packageName)

  return companionRenderModel
}

private fun addRenderDefaultTemplateValues(renderTemplateModel: RenderTemplateModel, packageName: String) =
  renderTemplateModel.newTemplate.parameters.run {
    filterIsInstance<StringParameter>().forEach { it.value = it.suggest() ?: it.value }
    val packageNameParameter = find { it.name == "Package name" } as StringParameter?
    packageNameParameter?.value = packageName
  }

private fun getModuleName(formFactor: FormFactor): String =
  // Form factors like Android Auto build upon another form factor
  formFactor.name.replace("\\s".toRegex(), "_").lowercase(Locale.US)
