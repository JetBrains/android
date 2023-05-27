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
import com.google.common.truth.Truth
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.konan.file.File
import org.mockito.Mockito

class ApkStepTest : LightPlatformTestCase() {
  private val myWizard = Mockito.mock(ExportSignedPackageWizard::class.java)

  override fun setUp() {
    super.setUp()
    whenever(myWizard.project).thenReturn(project)
    whenever(myWizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
  }

  fun testGetHelpId() {
    val apkStep = ApkStep(myWizard)
    Truth.assertThat(apkStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }

  fun testInitialDestinationNotSet() {
    val apkStep = ApkStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = ModuleRootManager.getInstance(module).contentRoots[0].path + File.separator + module.name + ".apk"
    Truth.assertThat(apkStep.getInitialPath(properties, module)).isEqualTo(projectPath)
  }

  fun testInitialDestinationSet() {
    val apkStep = ApkStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val testPath = this.homePath + File.separator + "custom.apk"
    properties.setValue(apkStep.getApkPathPropertyName(module.name), testPath)
    Truth.assertThat(apkStep.getInitialPath(properties, module)).isEqualTo(testPath)
  }
}