/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test

class InspectorBannerTest {

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testInitiallyHidden() {
    val banner = InspectorBanner(projectRule.project)
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testVisibleWithStatus() {
    val banner = InspectorBanner(projectRule.project)
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    bannerService.addNotification("There is an error somewhere", Status.Error, emptyList())
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertThat(banner.isVisible).isTrue()
  }

  @Test
  fun testInvisibleAfterEmptyStatus() {
    val banner = InspectorBanner(projectRule.project)
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    bannerService.addNotification("There is an error somewhere", Status.Error, emptyList())
    bannerService.clear()
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertThat(banner.isVisible).isFalse()
  }
}
