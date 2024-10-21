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
package com.android.tools.studio.labs

import com.android.tools.analytics.UsageTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioLabsEvent
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ApplicationRule
import icons.StudioIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

// TODO(b/371060411): Add more tests, figure out how to test compose components in JSwing
class StudioLabsSettingsConfigurableTest {

  private val configurable: StudioLabsSettingsConfigurable = StudioLabsSettingsConfigurable()

  @get:Rule val usageTrackerRule = UsageTrackerRule()
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun configurable_hasStudioLabsIcon() {
    assertThat(configurable.promoIcon).isEqualTo(StudioIcons.Shell.Menu.STUDIO_LABS)
  }

  @Test
  fun configurable_createPanel_logsOpenedEvent(): Unit =
    // ComposePanel can only be created inside AWT Event Dispatch Thread.
    runBlocking(Dispatchers.EDT) {
      configurable.createPanel()

      assertThat(usageTrackerRule.studioLabsUsageEvents())
        .containsExactly(
          StudioLabsEvent.newBuilder()
            .setPageInteraction(StudioLabsEvent.PageInteraction.OPENED)
            .build()
        )
    }

  @Test
  fun configurable_onApply_logsApplyEvent() {
    configurable.apply()

    assertThat(usageTrackerRule.studioLabsUsageEvents())
      .containsExactly(
        StudioLabsEvent.newBuilder()
          .setPageInteraction(StudioLabsEvent.PageInteraction.APPLY_BUTTON_CLICKED)
          .build()
      )
  }

  private fun UsageTrackerRule.studioLabsUsageEvents(): List<StudioLabsEvent> {
    return this.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_LABS_EVENT }
      .map { it.studioEvent.studioLabsEvent }
  }
}
