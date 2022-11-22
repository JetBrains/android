/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.ui

import com.intellij.openapi.externalSystem.service.ui.addJdkReferenceItem
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import org.jetbrains.plugins.gradle.util.getSelectedGradleJvmReference
import org.jetbrains.plugins.gradle.util.nonblockingResolveGradleJvmInfo
import org.jetbrains.plugins.gradle.util.setSelectedGradleJvmReference
import java.awt.Component

/**
 * Wrapper on top of IntelliJ component [SdkComboBox] that presents a dropdown list of different [SdkListItem.SdkItem], grouping them
 * depending on their kind [SdkListItem.ProjectSdkItem], [SdkListItem.SuggestedItem], [SdkListItem.SdkReferenceItem], etc..
 */
@Suppress("UnstableApiUsage")
class GradleJdkComboBox(
  sdkComboBoxModel: SdkComboBoxModel,
  private val sdkLookupProvider: SdkLookupProvider,
  private val externalProjectPath: String?
) {

  private val sdkReferenceItemsHomePathMap = mutableMapOf<String, String?>()
  private val comboBox = SdkComboBox(sdkComboBoxModel)
  private val model
    get() = comboBox.model

  val component: Component = comboBox
  var selectedGradleJvmReference: String?
    get() = comboBox.getSelectedGradleJvmReference(sdkLookupProvider)
    set(value) {
      comboBox.setSelectedGradleJvmReference(
        sdkLookupProvider = sdkLookupProvider,
        externalProjectPath = externalProjectPath,
        jdkReference = value
      )
    }

  init {
    comboBox.renderer = GradleJdkListPathPresenter(
      sdkReferenceItemsHomePathMap = sdkReferenceItemsHomePathMap,
      producerSdkList = { model.listModel }
    )
  }

  fun isModelModified() = comboBox.model.sdksModel.isModified

  fun getProjectSdk(): Sdk? = comboBox.model.sdksModel.projectSdk

  fun getSelectedGradleJvmInfo() = sdkLookupProvider.nonblockingResolveGradleJvmInfo(
    project = model.project,
    projectSdk = getProjectSdk(),
    externalProjectPath = externalProjectPath,
    gradleJvm = selectedGradleJvmReference
  )

  fun addJdkReferenceItem(name: String, homePath: String?) {
    comboBox.addJdkReferenceItem(name, homePath)
    sdkReferenceItemsHomePathMap[name] = homePath
  }

  fun applyModelChanges() {
    comboBox.model.sdksModel.apply()
  }
}