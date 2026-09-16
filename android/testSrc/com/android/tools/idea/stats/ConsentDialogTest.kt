/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.idea.stats.ConsentDialog.Companion.DISABLE_DIALOG_PROPERTY
import com.android.tools.idea.stats.ConsentDialog.Companion.ENABLE_DIALOG_PROPERTY
import com.intellij.testFramework.ApplicationRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConsentDialogTest {
  @get:Rule val applicationRule = ApplicationRule()

  private var originalDisableValue: String? = null
  private var originalEnableValue: String? = null

  @Before
  fun setUp() {
    originalDisableValue = System.getProperty(DISABLE_DIALOG_PROPERTY)
    originalEnableValue = System.getProperty(ENABLE_DIALOG_PROPERTY)
  }

  @After
  fun tearDown() {
    restoreProperty(DISABLE_DIALOG_PROPERTY, originalDisableValue)
    restoreProperty(ENABLE_DIALOG_PROPERTY, originalEnableValue)
  }

  private fun restoreProperty(key: String, value: String?) {
    if (value != null) {
      System.setProperty(key, value)
    } else {
      System.clearProperty(key)
    }
  }

  @Test
  fun testDisablePropertyPrecedence() {
    // Verify dialog is hidden by default in tests (no property set)
    assertFalse("Dialog should be disabled by default in tests", ConsentDialog.shouldShowConsentDialog)

    // Enable dialog for tests so it would normally be shown
    System.setProperty(ENABLE_DIALOG_PROPERTY, "true")
    // Verify it is shown (enabled)
    assertTrue("Dialog should be enabled for tests when property is set", ConsentDialog.shouldShowConsentDialog)

    // Now disable it explicitly
    System.setProperty(DISABLE_DIALOG_PROPERTY, "true")
    // Verify it is NOT shown (disabled)
    assertFalse("Dialog should be disabled when disable property is set, even if enabled for tests", ConsentDialog.shouldShowConsentDialog)
  }
}
