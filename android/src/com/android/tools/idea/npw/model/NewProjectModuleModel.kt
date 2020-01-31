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
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.templates.CircularParameterDependencyException
import com.android.tools.idea.templates.Parameter
import com.android.tools.idea.templates.ParameterValueResolver
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.templates.TemplateManager.CATEGORY_ACTIVITY
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.Locale

private val log: Logger get() = logger<NewProjectModuleModel>()

class NewProjectModuleModel(private val projectModel: NewProjectModel) : WizardModel() {
  @JvmField
  val formFactor = ObjectValueProperty(FormFactor.MOBILE)
  private val newModuleModel = NewModuleModel(projectModel, File(""), createDummyTemplate(), formFactor)
  /**
   * A model which is used at the optional step after usual activity configuring. Currently only used for Android Things.
   */
  @JvmField
  val extraRenderTemplateModel = RenderTemplateModel.fromModuleModel(newModuleModel, null, message("android.wizard.config.activity.title"))
  @JvmField
  val renderTemplateHandle = OptionalValueProperty<TemplateHandle>()
  @JvmField
  val hasCompanionApp = BoolValueProperty()

  fun androidSdkInfo(): OptionalProperty<AndroidVersionsInfo.VersionItem> = newModuleModel.androidSdkInfo

  fun moduleTemplateFile(): OptionalProperty<File> = newModuleModel.templateFile

  override fun handleFinished() {
    initMainModule()

    if (hasCompanionApp.get()) {
      val companionModuleModel = createCompanionModuleModel(projectModel)
      val companionRenderModel = createCompanionRenderModel(companionModuleModel)

      companionModuleModel.androidSdkInfo.value = androidSdkInfo().value

      companionModuleModel.handleFinished()
      companionRenderModel.handleFinished()
    }

    val newRenderTemplateModel = createMainRenderModel()

    newModuleModel.handleFinished()

    if (newRenderTemplateModel == extraRenderTemplateModel) {
      return // Extra render is driven by the Wizard itself
    }

    if (newRenderTemplateModel.templateHandle != null) {
      addRenderDefaultTemplateValues(newRenderTemplateModel)
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

    val projectLocation = projectModel.projectLocation.get()

    newModuleModel.moduleName.set(moduleName)
    newModuleModel.template.set(createDefaultTemplateAt(projectLocation, moduleName))
  }

  private fun createMainRenderModel(): RenderTemplateModel = when {
    projectModel.enableCppSupport.get() -> createCompanionRenderModel(newModuleModel)
    extraRenderTemplateModel.templateHandle == null ->
      RenderTemplateModel.fromModuleModel(newModuleModel, renderTemplateHandle.valueOrNull)
    else -> extraRenderTemplateModel // Extra Render is visible. Use it.
  }
}

private const val EMPTY_ACTIVITY = "Empty Activity"
private const val ANDROID_MODULE = "Android Module"

private fun createCompanionModuleModel(projectModel: NewProjectModel): NewModuleModel {
  // Note: The companion Module is always a Mobile app
  val moduleTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_MODULE)
  val moduleName = getModuleName(FormFactor.MOBILE)
  val namedModuleTemplate = createDefaultTemplateAt(projectModel.projectLocation.get(), moduleName)
  val companionModuleModel = NewModuleModel(projectModel, moduleTemplateFile!!, namedModuleTemplate)
  companionModuleModel.moduleName.set(moduleName)

  return companionModuleModel
}

private fun createCompanionRenderModel(moduleModel: NewModuleModel): RenderTemplateModel {
  // Note: The companion Render is always a "Empty Activity"
  val renderTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_ACTIVITY, EMPTY_ACTIVITY)
  val renderTemplateHandle = TemplateHandle(renderTemplateFile!!)

  val companionRenderModel = RenderTemplateModel.fromModuleModel(moduleModel, renderTemplateHandle)
  addRenderDefaultTemplateValues(companionRenderModel)

  return companionRenderModel
}

private fun getModuleName(formFactor: FormFactor): String =
  // Form factors like Android Auto build upon another form factor
  (formFactor.baseFormFactor ?: formFactor).id.replace("\\s".toRegex(), "_").toLowerCase(Locale.US)

private fun addRenderDefaultTemplateValues(renderTemplateModel: RenderTemplateModel) {
  val templateValues = renderTemplateModel.templateValues
  val templateMetadata = renderTemplateModel.templateHandle!!.metadata
  val userValues = hashMapOf<Parameter, Any>()
  val additionalValues = hashMapOf<String, Any>()

  val packageName = renderTemplateModel.packageName.get()
  TemplateValueInjector(additionalValues).addTemplateAdditionalValues(packageName, renderTemplateModel.template)

  try {
    val parameterValues = ParameterValueResolver.resolve(templateMetadata.parameters, userValues, additionalValues)
    parameterValues.forEach { (parameter, value) -> templateValues[parameter.id!!] = value }
  }
  catch (e: CircularParameterDependencyException) {
    log.error("Circular dependency between parameters in template %1\$s", e, templateMetadata.title!!)
  }
}
