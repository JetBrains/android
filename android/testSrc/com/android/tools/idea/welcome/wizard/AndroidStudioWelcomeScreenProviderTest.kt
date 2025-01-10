/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizardHost
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import javax.swing.JRootPane
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
class AndroidStudioWelcomeScreenProviderTest {

  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain =
    RuleChain(projectRule, EdtRule()) // AndroidProjectRule must get initialized off the EDT thread

  lateinit var mockAndroidStudioWelcomeScreenService: AndroidStudioWelcomeScreenService

  @Before
  fun setUp() {
    mockAndroidStudioWelcomeScreenService =
      projectRule.mockService(AndroidStudioWelcomeScreenService::class.java)
  }

  @Test
  fun isAvailable_returnsValueFromService() {
    whenever(mockAndroidStudioWelcomeScreenService.isAvailable()).thenReturn(true)
    assertTrue { AndroidStudioWelcomeScreenProvider().isAvailable }
    whenever(mockAndroidStudioWelcomeScreenService.isAvailable()).thenReturn(false)
    assertFalse { AndroidStudioWelcomeScreenProvider().isAvailable }
  }

  @Test
  fun createWelcomeScreen_marksWizardAsShownInService() {
    whenever(mockAndroidStudioWelcomeScreenService.getWizardMode(any(), anyOrNull(), any()))
      .thenReturn(FirstRunWizardMode.NEW_INSTALL)
    AndroidStudioWelcomeScreenProvider().createWelcomeScreen(JRootPane())
    verify(mockAndroidStudioWelcomeScreenService).wizardWasShown = true
  }

  @Test
  fun createWelcomeScreen_returnsOldWizard_whenNewWizardFlagFalse() {
    whenever(mockAndroidStudioWelcomeScreenService.getWizardMode(any(), anyOrNull(), any()))
      .thenReturn(FirstRunWizardMode.NEW_INSTALL)
    StudioFlags.FIRST_RUN_MIGRATED_WIZARD_ENABLED.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    val screen = AndroidStudioWelcomeScreenProvider().createWelcomeScreen(JRootPane())

    assertTrue { screen is FirstRunWizardHost }
  }

  @Test
  fun createWelcomeScreen_returnsNewWizard_whenNewWizardFlagTrue() {
    whenever(mockAndroidStudioWelcomeScreenService.getWizardMode(any(), anyOrNull(), any()))
      .thenReturn(FirstRunWizardMode.NEW_INSTALL)
    StudioFlags.FIRST_RUN_MIGRATED_WIZARD_ENABLED.overrideForTest(
      true,
      projectRule.testRootDisposable,
    )
    val screen = AndroidStudioWelcomeScreenProvider().createWelcomeScreen(JRootPane())

    assertTrue { screen is StudioFirstRunWelcomeScreen }
  }
}
