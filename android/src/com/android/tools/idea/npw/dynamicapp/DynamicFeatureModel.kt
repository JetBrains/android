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

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.collections.ObservableList
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_FUSING
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_IS_INSTANT_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_DYNAMIC_FEATURE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_SIMPLE_NAME
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class DynamicFeatureModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker, isInstant: Boolean
) : ModuleModel(project, templateHandle, projectSyncInvoker, "dynamicfeature") {
  @JvmField val featureTitle = StringValueProperty("Module Title")
  @JvmField val packageName = StringValueProperty()
  @JvmField val androidSdkInfo = OptionalValueProperty<VersionItem>()
  @JvmField val baseApplication = OptionalValueProperty<Module>()
  @JvmField val featureOnDemand = BoolValueProperty(true)
  @JvmField val featureFusing = BoolValueProperty(true)
  @JvmField val instantModule = BoolValueProperty(false)
  @JvmField val deviceFeatures = ObservableList<DeviceFeatureModel>()
  @JvmField val downloadInstallKind =
    OptionalValueProperty(if (isInstant) DownloadInstallKind.INCLUDE_AT_INSTALL_TIME else DownloadInstallKind.ON_DEMAND_ONLY)

  override val renderer = object: ModuleTemplateRenderer() {
    override fun init() {
      super.init()
      val modulePaths = createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths

      val newValues = mutableMapOf(
          ATTR_IS_DYNAMIC_FEATURE to true,
          ATTR_MODULE_SIMPLE_NAME to nameToJavaPackage(moduleName.get()),
          ATTR_DYNAMIC_FEATURE_TITLE to featureTitle.get(),
          ATTR_DYNAMIC_FEATURE_ON_DEMAND to featureOnDemand.get(),
          ATTR_DYNAMIC_FEATURE_FUSING to featureFusing.get(),
          ATTR_IS_NEW_MODULE to true,
          ATTR_IS_LIBRARY_MODULE to false,
          ATTR_DYNAMIC_IS_INSTANT_MODULE to instantModule.get(),
          // Dynamic delivery conditions
          ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY to (downloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME),
          ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY to (downloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS),
          ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY to (downloadInstallKind.value == DownloadInstallKind.ON_DEMAND_ONLY),
          ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST to deviceFeatures
      )

      TemplateValueInjector(newValues)
        .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
        .setBuildVersion(androidSdkInfo.value, project).setBaseFeature(baseApplication.value)

      templateValues.putAll(newValues)
    }
  }
}