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

//import com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.Companion.GRADLE_LOCAL_JAVA_HOME
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.createJdkInfo
import com.intellij.openapi.externalSystem.service.ui.addJdkReferenceItem
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.jetbrains.plugins.gradle.util.getSelectedGradleJvmReference
import org.jetbrains.plugins.gradle.util.nonblockingResolveGradleJvmInfo
import org.jetbrains.plugins.gradle.util.setSelectedGradleJvmReference
import java.awt.Component
import java.io.File

/**
 * Wrapper on top of IntelliJ component [SdkComboBox] that presents a dropdown list of different [SdkListItem.SdkItem], grouping them
 * depending on their kind [SdkListItem.ProjectSdkItem], [SdkListItem.SuggestedItem], [SdkListItem.SdkReferenceItem], etc..
 */
@Suppress("UnstableApiUsage")
class GradleJdkComboBox(
  sdkComboBoxModel: SdkComboBoxModel,
  private val sdkLookupProvider: SdkLookupProvider,
  private val externalProjectFile: File
) {

  private val sdkReferenceItemsHomePathMap = mutableMapOf<String, String?>()
  private val comboBox = SdkComboBox(sdkComboBoxModel)
  private val model
    get() = comboBox.model
  /*
  private val gradleLocalJavaHomeVersion: String
    get() = JavaSdk.getInstance().getVersionString(gradleLocalJavaHome).orEmpty()
   */
  private val gradleLocalJavaHome: String
    get() = GradleConfigProperties(externalProjectFile).javaHome?.toString().orEmpty()

  val component: Component = comboBox
  var selectedGradleJvmReference: String?
    get() = when (val item = comboBox.selectedItem) {
      is SdkListItem.SdkReferenceItem -> when (item.name) {
        //GRADLE_LOCAL_JAVA_HOME -> USE_GRADLE_LOCAL_JAVA_HOME
        else -> comboBox.getSelectedGradleJvmReference(sdkLookupProvider)
      }
      else -> comboBox.getSelectedGradleJvmReference(sdkLookupProvider)
    }
    set(value) {
      when (value) {
        USE_JAVA_HOME -> comboBox.selectedItem = comboBox.addJdkReferenceItem(
          name = JAVA_HOME,
          homePath = IdeSdks.getInstance().jdkFromJavaHome
        )
        /*
        USE_GRADLE_LOCAL_JAVA_HOME -> comboBox.selectedItem = comboBox.addJdkReferenceItem(
          name = GRADLE_LOCAL_JAVA_HOME,
          versionString = gradleLocalJavaHomeVersion,
          isValid = true
        )
         */
        else -> comboBox.setSelectedGradleJvmReference(
          sdkLookupProvider = sdkLookupProvider,
          externalProjectPath = externalProjectFile.absolutePath,
          jdkReference = value
        )
      }
    }

  init {
    comboBox.renderer = GradleJdkListPathPresenter(
      sdkReferenceItemsHomePathMap = sdkReferenceItemsHomePathMap,
      producerSdkList = { model.listModel }
    )
  }

  fun isModelModified() = comboBox.model.sdksModel.isModified

  fun getProjectSdk(): Sdk? = comboBox.model.sdksModel.projectSdk

  fun getSelectedGradleJvmInfo() = when (selectedGradleJvmReference) {
    USE_JAVA_HOME -> createJdkInfo(
      name = JAVA_HOME,
      homePath = IdeSdks.getInstance().jdkFromJavaHome
    )
    /*
    USE_GRADLE_LOCAL_JAVA_HOME -> createJdkInfo(
      name = GRADLE_LOCAL_JAVA_HOME,
      homePath = gradleLocalJavaHome
    )
     */
    else -> sdkLookupProvider.nonblockingResolveGradleJvmInfo(
      project = model.project,
      projectSdk = getProjectSdk(),
      externalProjectPath = externalProjectFile.absolutePath,
      gradleJvm = selectedGradleJvmReference
    ).takeUnless { it == SdkLookupProvider.SdkInfo.Unresolved || it == SdkLookupProvider.SdkInfo.Undefined }
            ?: sdkLookupProvider.nonblockingResolveSdkBySdkName(selectedGradleJvmReference)
  }

  fun addJdkReferenceItem(name: String, homePath: String, isValid: Boolean): SdkListItem {
    sdkReferenceItemsHomePathMap[name] = homePath
    val versionString = JavaSdk.getInstance().getVersionString(homePath).orEmpty()
    return comboBox.addJdkReferenceItem(name, versionString, isValid)
  }

  fun addJdkReferenceItem(name: String, homePath: String) {
    comboBox.addJdkReferenceItem(name, homePath)
    sdkReferenceItemsHomePathMap[name] = homePath
  }

  fun applyModelChanges() {
    comboBox.model.sdksModel.apply()
  }

  fun addItemSelectedLister(onSelected: (SdkListItem) -> Unit) {
    comboBox.whenItemSelected {
      onSelected(it)
    }
  }

  fun updateJdkReferenceItem(name: String, homePath: String) {
    val updatedJdkReferenceIdem = addJdkReferenceItem(
      name = name,
      homePath = homePath,
      isValid = true
    )
    comboBox.model.selectedItem = updatedJdkReferenceIdem
  }

  private fun SdkLookupProvider.nonblockingResolveSdkBySdkName(sdkName: String?): SdkLookupProvider.SdkInfo {
    if (sdkName == null) return getSdkInfo()
    newLookupBuilder()
      .withSdkName(sdkName)
      .withSdkType(ExternalSystemJdkUtil.getJavaSdkType())
      .executeLookup()
    return getSdkInfo()
  }
}