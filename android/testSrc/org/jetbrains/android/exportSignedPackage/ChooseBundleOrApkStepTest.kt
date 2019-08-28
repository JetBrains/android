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
package org.jetbrains.android.exportSignedPackage

import com.intellij.testFramework.JavaProjectTestCase
import org.mockito.Mockito

class ChooseBundleOrApkStepTest : JavaProjectTestCase() {
  fun testSetup() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    Mockito.`when`(wizard.project).thenReturn(myProject)

    val chooseStep = ChooseBundleOrApkStep(wizard)
    assertTrue(chooseStep.myBundleButton.isEnabled)
    assertTrue(chooseStep.myBundleButton.isSelected)
  }

  fun testApkSelectedThroughSetting() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    Mockito.`when`(wizard.project).thenReturn(myProject)

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.BUILD_TARGET_KEY = ExportSignedPackageWizard.APK
    val chooseStep = ChooseBundleOrApkStep(wizard)
    assertTrue(chooseStep.myBundleButton.isEnabled)
    assertFalse(chooseStep.myBundleButton.isSelected)
    assertTrue(chooseStep.myApkButton.isSelected)
  }
}
