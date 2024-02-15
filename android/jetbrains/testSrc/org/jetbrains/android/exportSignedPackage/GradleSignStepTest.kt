/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage

import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType
import org.mockito.Mockito
import org.mockito.Mockito.verify
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

class GradleSignStepTest : LightPlatformTestCase() {
  private var myWizard = Mockito.mock(ExportSignedPackageWizard::class.java)
  override fun setUp() {
    super.setUp()
    whenever(myWizard.project).thenReturn(project)
    whenever(myWizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
  }

  fun testGetHelpId() {
    val gradleSignStep = GradleSignStep(myWizard)
    assertThat(gradleSignStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }

  fun testInitialDestinationApkNotSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = project.baseDir.path
    // Set Bundle to confirm it is not the same
    val bundlePath = this.homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(projectPath)
  }

  fun testInitialDestinationBundleNotSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = project.baseDir.path
    // Set Apk to confirm it is not the same
    val apkPath = this.homePath + File.separator + "Apk"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(projectPath)
  }

  fun testInitialDestinationApkSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = this.homePath + File.separator + "Apk"
    val bundlePath = this.homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(apkPath)
  }

  fun testInitialDestinationBundleSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = this.homePath + File.separator + "Apk"
    val bundlePath = this.homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(bundlePath)
  }

  fun testApkDestinationEndsWhiteSpace() {
    verifyDestinationEndsWhiteSpace(ExportSignedPackageWizard.APK)
  }

  fun testBundleDestinationEndsWhiteSpace() {
    verifyDestinationEndsWhiteSpace(ExportSignedPackageWizard.BUNDLE)
  }

  private fun verifyDestinationEndsWhiteSpace(targetType: TargetType) {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance(project)
    val destinationPath = "${this.homePath}${File.separator}$targetType "
    whenever(myWizard.targetType).thenReturn(targetType)
    val testAndroidModel = Mockito.mock(GradleAndroidModel::class.java)
    whenever(testAndroidModel.moduleName).thenReturn(name)
    whenever(testAndroidModel.variantNames).thenReturn(listOf("debug", "release"))
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, targetType), destinationPath)
    properties.setList(GradleSignStep.PROPERTY_BUILD_VARIANTS, listOf("release"))

    val apkDir = File(destinationPath)
    if (!apkDir.exists()) {
      assertThat(apkDir.mkdirs()).isTrue()
    }
    gradleSignStep._init(testAndroidModel)
    val captor = argumentCaptor<String>()
    gradleSignStep.commitForNext()
    verify(myWizard).setApkPath(captor.capture())
    assertThat(Files.isSameFile(Path(captor.value), Path(destinationPath))).isTrue()
  }
}