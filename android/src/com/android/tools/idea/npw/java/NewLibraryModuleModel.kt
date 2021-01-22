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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion

class NewLibraryModuleModel(
  project: Project,
  moduleParent: String,
  projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(
  name = "lib",
  commandName = "New Library Module",
  isLibrary = true,
  projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
  moduleParent = moduleParent,
  wizardContext = NEW_MODULE
) {
  @JvmField
  val className = StringValueProperty("MyClass")

  // TODO(qumeric): will it fail if there are no SDKs installed?
  override val androidSdkInfo = OptionalValueProperty(
    AndroidVersionsInfo().apply { loadLocalVersions() }
      .getKnownTargetVersions(FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API)
      .first() // we don't care which one do we use, we just have to pass something, it is not going to be used
  )

  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = RenderLoggingEvent.JAVA_LIBRARY

  override val renderer = object : ModuleTemplateRenderer() {
    override val recipe: Recipe get() = { td: TemplateData ->
      generatePureLibrary(td as ModuleTemplateData, className.get(), useGradleKts.get())
    }
  }
}
