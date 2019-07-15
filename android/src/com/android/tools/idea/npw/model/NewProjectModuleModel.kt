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
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.templates.CircularParameterDependencyException
import com.android.tools.idea.templates.Parameter
import com.android.tools.idea.templates.ParameterValueResolver
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.templates.TemplateManager.CATEGORY_ACTIVITY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_INCLUDE_FORM_FACTOR
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.collect.Maps
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.Locale

class NewProjectModuleModel(private val myProjectModel: NewProjectModel) : WizardModel() {
  private val myNewModuleModel: NewModuleModel
  val extraRenderTemplateModel: RenderTemplateModel
  private val myFormFactor = ObjectValueProperty(FormFactor.MOBILE)
  private val myRenderTemplateHandle = OptionalValueProperty<TemplateHandle>()

  private val myHasCompanionApp = BoolValueProperty()

  init {
    myNewModuleModel = NewModuleModel(myProjectModel, File(""), createDummyTemplate())
    extraRenderTemplateModel = RenderTemplateModel.fromModuleModel(myNewModuleModel, null, message("android.wizard.config.activity.title"))
  }

  fun androidSdkInfo(): OptionalProperty<AndroidVersionsInfo.VersionItem> {
    return myNewModuleModel.androidSdkInfo
  }

  fun moduleTemplateFile(): OptionalProperty<File> {
    return myNewModuleModel.templateFile
  }

  fun renderTemplateHandle(): OptionalProperty<TemplateHandle> {
    return myRenderTemplateHandle
  }

  fun hasCompanionApp(): BoolProperty {
    return myHasCompanionApp
  }

  fun formFactor(): ObjectProperty<FormFactor> {
    return myFormFactor
  }

  override fun handleFinished() {
    myProjectModel.newModuleModels.clear()

    val hasCompanionApp = myHasCompanionApp.get()

    initMainModule()

    val projectTemplateValues = myProjectModel.templateValues
    addModuleToProject(myNewModuleModel, myFormFactor.get(), myProjectModel, projectTemplateValues)

    if (hasCompanionApp) {
      val companionModuleModel = createCompanionModuleModel(myProjectModel)
      val companionRenderModel = createCompanionRenderModel(companionModuleModel)
      addModuleToProject(companionModuleModel, FormFactor.MOBILE, myProjectModel, projectTemplateValues)

      companionModuleModel.androidSdkInfo.value = androidSdkInfo().value
      companionModuleModel.setRenderTemplateModel(companionRenderModel)

      companionModuleModel.handleFinished()
      companionRenderModel.handleFinished()
    }

    val newRenderTemplateModel = createMainRenderModel()
    myNewModuleModel.setRenderTemplateModel(newRenderTemplateModel)

    val hasActivity = newRenderTemplateModel.templateHandle != null
    if (hasActivity && newRenderTemplateModel != extraRenderTemplateModel) { // Extra render is driven by the Wizard itself
      addRenderDefaultTemplateValues(newRenderTemplateModel)
    }

    myNewModuleModel.handleFinished()
    if (newRenderTemplateModel != extraRenderTemplateModel) { // Extra render is driven by the Wizard itself
      if (hasActivity) {
        newRenderTemplateModel.handleFinished()
      }
      else {
        newRenderTemplateModel.handleSkipped() // "No Activity" selected
      }
    }
  }

  private fun initMainModule() {
    val moduleName: String
    if (myHasCompanionApp.get()) {
      moduleName = getModuleName(myFormFactor.get())
    }
    else {
      moduleName = SdkConstants.APP_PREFIX
    }

    val projectLocation = myProjectModel.projectLocation.get()

    myNewModuleModel.moduleName.set(moduleName)
    myNewModuleModel.template.set(createDefaultTemplateAt(projectLocation, moduleName))
  }

  private fun createMainRenderModel(): RenderTemplateModel {
    val newRenderTemplateModel: RenderTemplateModel
    if (myProjectModel.enableCppSupport.get()) {
      newRenderTemplateModel = createCompanionRenderModel(myNewModuleModel)
    }
    else if (extraRenderTemplateModel.templateHandle == null) {
      newRenderTemplateModel = RenderTemplateModel.fromModuleModel(myNewModuleModel, null, "")
      newRenderTemplateModel.templateHandle = renderTemplateHandle().valueOrNull
    }
    else { // Extra Render is visible. Use it.
      newRenderTemplateModel = extraRenderTemplateModel
    }
    return newRenderTemplateModel
  }
}

private const val EMPTY_ACTIVITY = "Empty Activity"
private const val ANDROID_MODULE = "Android Module"

private fun addModuleToProject(moduleModel: NewModuleModel, formFactor: FormFactor,
                               projectModel: NewProjectModel, projectTemplateValues: MutableMap<String, Any>) {
  projectTemplateValues[formFactor.id + ATTR_INCLUDE_FORM_FACTOR] = true
  projectTemplateValues[formFactor.id + ATTR_MODULE_NAME] = moduleModel.moduleName.get()
  projectModel.newModuleModels.add(moduleModel)
}

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

  val companionRenderModel = RenderTemplateModel.fromModuleModel(moduleModel, renderTemplateHandle, "")
  addRenderDefaultTemplateValues(companionRenderModel)

  return companionRenderModel
}

private fun getModuleName(formFactor: FormFactor): String {
  var formFactor = formFactor
  if (formFactor.baseFormFactor != null) {
    // Form factors like Android Auto build upon another form factor
    formFactor = formFactor.baseFormFactor!!
  }
  return formFactor.id.replace("\\s".toRegex(), "_").toLowerCase(Locale.US)
}

private fun addRenderDefaultTemplateValues(renderTemplateModel: RenderTemplateModel) {
  val templateValues = renderTemplateModel.templateValues
  val templateMetadata = renderTemplateModel.templateHandle!!.metadata
  val userValues = Maps.newHashMap<Parameter, Any>()
  val additionalValues = Maps.newHashMap<String, Any>()

  val packageName = renderTemplateModel.packageName.get()
  TemplateValueInjector(additionalValues).addTemplateAdditionalValues(packageName, renderTemplateModel.template)
  additionalValues[ATTR_PACKAGE_NAME] = renderTemplateModel.packageName.get()

  try {
    val renderParameters = templateMetadata.parameters
    val parameterValues = ParameterValueResolver.resolve(renderParameters, userValues, additionalValues)
    parameterValues.forEach { (parameter, value) -> templateValues[parameter.id!!] = value }
  }
  catch (e: CircularParameterDependencyException) {
    log.error("Circular dependency between parameters in template %1\$s", e, templateMetadata.title!!)
  }
}

private val log: Logger
  get() = Logger.getInstance(NewProjectModuleModel::class.java)
