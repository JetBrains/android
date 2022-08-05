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

import com.android.testutils.MockitoAwareLightPlatformTestCase
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.google.common.truth.Truth
import org.mockito.Mockito

class ApkStepTest : MockitoAwareLightPlatformTestCase() {
  fun testGetHelpId() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val apkStep = ApkStep(wizard)
    Truth.assertThat(apkStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }
}