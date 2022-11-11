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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.LightPlatformTestCase
import org.mockito.Mockito
import java.io.File

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
    val bundlePath = this.homePath + File.pathSeparator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(projectPath)
  }

  fun testInitialDestinationBundleNotSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = project.baseDir.path
    // Set Apk to confirm it is not the same
    val apkPath = this.homePath + File.pathSeparator + "Apk"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(projectPath)
  }

  fun testInitialDestinationApkSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = this.homePath + File.pathSeparator + "Apk"
    val bundlePath = this.homePath + File.pathSeparator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(apkPath)
  }

  fun testInitialDestinationBundleSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = this.homePath + File.pathSeparator + "Apk"
    val bundlePath = this.homePath + File.pathSeparator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(bundlePath)
  }
}