/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.androidModule.generateAndroidModule
import com.android.tools.idea.npw.module.recipes.ndk.generateBasicJniBindings
import com.android.tools.idea.npw.module.recipes.ndk.generateCMakeFile
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE

class NewAndroidNativeModuleModel(
  projectModuleData: ProjectModelData,
  moduleParent: String,
  val cppStandard: OptionalValueProperty<CppStandardType> = OptionalValueProperty(CppStandardType.`Toolchain Default`)
) :
  ModuleModel(
    name = "",
    commandName = "New Native Module",
    isLibrary = true,
    projectModelData = projectModuleData,
    moduleParent = moduleParent,
    wizardContext = NEW_MODULE
  ) {

  init {
    applicationName.set("nativelib")
  }

  override val renderer = object : ModuleTemplateRenderer() {
    @WorkerThread
    override fun init() {
      super.init()

      moduleTemplateDataBuilder.apply {
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), this@NewAndroidNativeModuleModel.packageName.get())
      }
      val tff = formFactor.get()
      projectTemplateDataBuilder.includedFormFactorNames.putIfAbsent(tff, mutableListOf(moduleName.get()))?.add(moduleName.get())
    }

    override val recipe: Recipe
      get() = { data ->
        if (data !is ModuleTemplateData) {
          throw IllegalStateException()
        }
        generateAndroidModule(
          data = data,
          appTitle = applicationName.get(),
          useKts = useGradleKts.get(),
          bytecodeLevel = BytecodeLevel.default,
          enableCpp = true,
          cppStandard = cppStandard.value
        )
        val nativeSourceName = "native-lib.cpp"
        generateCMakeFile(data, nativeSourceName)
        generateBasicJniBindings(data, language.get().orElse(Language.Java), "NativeLib", nativeSourceName)
      }
  }
  override val loggingEvent: AndroidStudioEvent.TemplateRenderer = AndroidStudioEvent.TemplateRenderer.ANDROID_NATIVE_MODULE
}