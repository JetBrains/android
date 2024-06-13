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
package com.android.tools.idea.common.surface.organization

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.tools.adtui.compose.StudioTestTheme
import com.intellij.util.ui.UIUtil
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class OrganizationHeaderTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun openAndCloseGroup() {
    val group = OrganizationGroup("method", "group")
    composeTestRule.setContent { StudioTestTheme(darkMode = false) { OrganizationHeader(group) } }
    assertEquals(true, group.isOpened.value)
    composeTestRule.onNodeWithTag("openButton").performClick()
    assertEquals(false, group.isOpened.value)
    composeTestRule.onNodeWithTag("openButton").performClick()
    assertEquals(true, group.isOpened.value)
  }

  @Test
  fun nameIsDisplayed() {
    val group = OrganizationGroup("method", "Organization Display Name")
    composeTestRule.setContent { StudioTestTheme(darkMode = false) { OrganizationHeader(group) } }
    composeTestRule
      .onNodeWithTag("displayName", true)
      .assertTextContains("Organization Display Name")
  }

  @Test
  fun createWrappedHeader() {
    UIUtil.invokeAndWaitIfNeeded {
      val group = OrganizationGroup("method", "group")
      val panel = createOrganizationHeader(group)
      assertNotNull(panel)
    }
  }

  @Test
  @Ignore("Visual test")
  fun previewHeader() {
    val group = OrganizationGroup("method", "Organization Group")
    singleWindowApplication(title = "Preview") {
      StudioTestTheme(darkMode = false) { OrganizationHeader(group) }
    }
  }
}
