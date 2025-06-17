/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo.DeliveryType.BANNER
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.HyperlinkLabel
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ServiceDeprecatedBannerTest {

  @get:Rule val projectRule = ProjectRule()

  private val tracker = mock<AppInsightsTracker>()
  private var deprecationData =
    DevServicesDeprecationData(
      "header",
      "description",
      "url",
      true,
      DevServicesDeprecationStatus.DEPRECATED,
    )

  @Test
  fun `update action logs update event`() {
    val banner = ServiceDeprecatedBanner.create(tracker, deprecationData) {}
    val updateLabel = banner.findAllDescendants<HyperlinkLabel>().first()
    updateLabel.doClick()

    verify(tracker)
      .logServiceDeprecated(
        eq(DevServicesDeprecationStatus.DEPRECATED),
        any(),
        eq(BANNER),
        eq(null),
        eq(null),
        eq(true),
        eq(null),
      )
  }

  @Test
  fun `moreInfo action logs moreInfo event`() {
    val banner = ServiceDeprecatedBanner.create(tracker, deprecationData) {}
    val moreInfoLabel = banner.findAllDescendants<HyperlinkLabel>().last()
    moreInfoLabel.doClick()

    verify(tracker)
      .logServiceDeprecated(
        eq(DevServicesDeprecationStatus.DEPRECATED),
        any(),
        eq(BANNER),
        eq(null),
        eq(true),
        eq(null),
        eq(null),
      )
  }

  @Test
  fun `banner contains description`() {
    val banner = ServiceDeprecatedBanner.create(tracker, deprecationData) {}

    assertThat(banner.text).isEqualTo(deprecationData.description)
  }
}
