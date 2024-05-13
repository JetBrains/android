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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.HeavyPlatformTestCase
import org.mockito.Mockito

class ChooseBundleOrApkStepTest : HeavyPlatformTestCase() {
  private lateinit var ideComponents: IdeComponents

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)
  }

  fun testSetup() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(myProject)

    val chooseStep = ChooseBundleOrApkStep(wizard)
    assertTrue(chooseStep.myBundleButton.isEnabled)
    assertTrue(chooseStep.myBundleButton.isSelected)
  }

  fun testApkSelectedThroughSetting() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(myProject)

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.BUILD_TARGET_KEY = ExportSignedPackageWizard.APK
    val chooseStep = ChooseBundleOrApkStep(wizard)
    assertTrue(chooseStep.myBundleButton.isEnabled)
    assertFalse(chooseStep.myBundleButton.isSelected)
    assertTrue(chooseStep.myApkButton.isSelected)
  }

  fun testGetHelpId() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(myProject)

    val chooseStep = ChooseBundleOrApkStep(wizard)
    assertThat(chooseStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }
}
